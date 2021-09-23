/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.hypothesis;

import com.powsybl.iidm.network.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * @author Chamseddine BENHAMED <chamseddine.benhamed at rte-france.com>
 */
public class GeneratorsStartupAlgorithm {
    private static final Logger LOGGER = LoggerFactory.getLogger(GeneratorsStartupAlgorithm.class);

    private static final String UNKNOWN_REGION = "UnknownRegion";
    private static final String REGION_CVG = "regionCvg";
    private static final String DOUBTFUL_PRECISION = "DOUBTFUL PRECISION IN groups2Qua";
    private static final String SS = "SS = {}";
    private static final String EQUILIBRIUM = "EQUIL = {}";

    List<Generator> startupGroupsPowerMax = new ArrayList<>();

    double nuclearBandSetting = 0.05;
    double thermalBandSetting = 0.05;
    double hydroBandSetting = 0.1;

    double lossCoefficient = 0;
    double defaultAbatementCoefficient = 0;

    StartupMarginalGroupType startUpMarginalGroupType = StartupMarginalGroupType.CLASSIC;

    //thresholds for voltage adjustment
    double pThreshold = 0.0;
    double qThreshold = 0.0;

    public void apply(Network network, StartupMarginalGroupType startUpMarginalGroupType, double defaultAbatementCoefficient,
                      double lossCoefficient, double pThreshold, double qThreshold, List<Generator> startupGroupsPowerMax) {

        this.lossCoefficient = lossCoefficient;
        this.defaultAbatementCoefficient = defaultAbatementCoefficient;
        this.pThreshold = pThreshold;
        this.qThreshold = qThreshold;
        this.startUpMarginalGroupType = startUpMarginalGroupType;
        this.startupGroupsPowerMax = startupGroupsPowerMax;

        // prepare startupGroupsPerConnectedComponent HashMap
        Map<Component, List<StartupGroup>> startupGroupsPerConnectedComponent = prepareStartupGroupsPerConnectedComponentMap(network);

        // for each connected component
        startupGroupsPerConnectedComponent.keySet().forEach(component -> {
            HashMap<String, StartupRegion> regions = new HashMap<>();
            regions.put(UNKNOWN_REGION, StartupRegion.builder().name(UNKNOWN_REGION).marginalGroups(new ArrayList<>()).build());

            // create the startupZone for that connected component
            StartupZone startupZone = StartupZone.builder()
                    .name("StartupZone" + component.getNum())
                    .num(component.getNum())
                    .startupType(StartupType.EMPIL_ECO)
                    .canStart(true)
                    .countries(new ArrayList<>())
                    .startupGroups(startupGroupsPerConnectedComponent.get(component))
                    .startedGroups(new ArrayList<>())
                    .imposedPower(0)
                    .regions(regions)
                    .build();

            // log component num
            LOGGER.debug("Component: {}", component.getNum());

            evaluateConsumption(network, startupZone); // real consumption + losses

            if (startupZone.getStartupType() == StartupType.EMPIL_ECO) {
                economicalStacking(startupZone);
            }

            if (startupZone.isCanStart()) {
                final double[] startedProduction = {0};

                startupZone.getStartedGroups().forEach(startupGroup -> {
                    startupGroup.getGenerator().setTargetP(startupGroup.getSetPointPower());
                    startedProduction[0] += startupGroup.getSetPointPower();

                    if ((startupGroup.getGenerator().getTargetP() >= pThreshold) &&
                            (startupGroup.getGenerator().getReactiveLimits().getMaxQ(0) - startupGroup.getGenerator().getReactiveLimits().getMinQ(0) >= qThreshold)) {
                        startupGroup.getGenerator().setVoltageRegulatorOn(true);
                        LOGGER.info("Primary Voltage Control in operation");
                    }
                });

                if (Math.abs(startedProduction[0] - startupZone.getConsumption()) > 1)  {
                    LOGGER.error("Wrong starting production units: load + loss = {} MW; started production {} MW", startupZone.getConsumption(), startedProduction);
                }
            }
        });
    }

    private Map<Component, List<StartupGroup>> prepareStartupGroupsPerConnectedComponentMap(Network network) {
        Map<Component, List<StartupGroup>> startupGroupsPerConnectedComponent = new HashMap<>();
        network.getGeneratorStream().forEach(generator -> {
            StartupGroup startupGroup = new StartupGroup(false, 0, 0, false, generator);
            Component component = generator.getRegulatingTerminal().getBusBreakerView().getConnectableBus().getConnectedComponent();
            startupGroupsPerConnectedComponent.computeIfAbsent(component, k -> new ArrayList<>());
            List<StartupGroup> l = startupGroupsPerConnectedComponent.get(component);
            l.add(startupGroup);
        });
        return startupGroupsPerConnectedComponent;
    }

    // calculate  zone global consumption + losses
    // if Mexico calculate consumption per zone region
    private void evaluateConsumption(Network network, StartupZone startupZone) {
        final double[] consZone = {0};
        final double[] consFictitious = {0};

        network.getLoadStream().forEach(load -> {
            if (load.getTerminal().getBusView().getBus().getConnectedComponent().getNum() == startupZone.getNum()) {
                consZone[0] += load.getP0() * (1 + lossCoefficient);
                if (load.getLoadType() == LoadType.FICTITIOUS) {
                    consFictitious[0] += load.getP0() * (1 + lossCoefficient);
                }
                // prepare zones consumption per region if Mexico is activated
                if (startupZone.getStartupType() == StartupType.EMPIL_ECO && startUpMarginalGroupType == StartupMarginalGroupType.MEXICO) {
                    String regName = load.getTerminal().getBusView().getBus().getVoltageLevel().getSubstation().getProperty(REGION_CVG);
                    if (regName != null) {
                        startupZone.getRegions().computeIfAbsent(regName, k -> StartupRegion.builder().name(regName).marginalGroups(new ArrayList<>()).build());
                        double oldValue = startupZone.getRegions().get(regName).getConsumption();
                        startupZone.getRegions().get(regName).setConsumption(oldValue + load.getP0() * (1 + lossCoefficient));
                    } else {
                        double oldValue = startupZone.getRegions().get(UNKNOWN_REGION).getConsumption();
                        startupZone.getRegions().get(UNKNOWN_REGION).setConsumption(oldValue + load.getP0() * (1 + lossCoefficient));
                    }
                }
            }
        });

        if (consFictitious[0] != 0) {
            LOGGER.info("Fictive injection : {} MW (added to the load)", consFictitious);
        }
        startupZone.setConsumption(consZone[0]);
    }

    private void economicalStacking(StartupZone startupZone) {
        double pMaxAvailable = 0;
        StartupGroup marginalGroup = null;

        // Evaluate available production
        pMaxAvailable = evaluateProd(startupZone);

        // secondary connected component without neither imposed production nor consumption
        if (startupZone.getNum() != 0 && Math.abs(startupZone.getImposedPower()) < 1. && Math.abs(startupZone.getConsumption()) < 1) {
            startupZone.setCanStart(false);
            return;
        }

        // verify that we can start groups
        if (startupZone.getImposedPower() + pMaxAvailable < startupZone.getConsumption()) {
            LOGGER.error("Starting production units impossible on area {} : too much imposed power or lack of production available", startupZone.getName());
            if (startupZone.getNum() == 0) {
                LOGGER.error("Principal connected component not treated");
            } else {
                LOGGER.error("Secondary connected component not treated");
            }
            startupZone.setCanStart(false);
            return;
        }

        // economical stacking :
        startupZone.getStartupGroups().sort(new StartupGroupComparator());

        double powerToBeStarted = startupZone.getConsumption() - startupZone.getImposedPower();
        LOGGER.info("Power to be started {}", powerToBeStarted);

        for (StartupGroup startupGroup : startupZone.getStartupGroups()) {
            if (!startupGroup.isUsable()) {
                LOGGER.error("startup group {} unusable", startupGroup.getGenerator().getNameOrId());
            }
            if (startupGroup.isImposed()) {
                // already handled
                continue;
            }

            if (startupGroupsPowerMax.contains(startupGroup.getGenerator())) {
                startupGroup.setSetPointPower(startupGroup.getAvailablePower());
                powerToBeStarted -= startupGroup.getAvailablePower();
                startupZone.getStartedGroups().add(startupGroup);
                if (powerToBeStarted <= 0) {
                    marginalGroup = startupGroup;
                    // if Mexico activated
                    if (startupZone.getStartupType() == StartupType.EMPIL_ECO && startUpMarginalGroupType == StartupMarginalGroupType.MEXICO) {
                        addStartedPowerToTheRegion(startupZone, startupGroup);
                    }
                    break;
                }
                continue;
            }

            double pMin = startupGroup.getGenerator().getMinP() < 0 ? startupGroup.getGenerator().getMinP() : 0;

            if (startupGroup.getAvailablePower() < powerToBeStarted) {
                powerToBeStarted -= startupGroup.getAvailablePower();
                startupGroup.setSetPointPower(startupGroup.getAvailablePower());
            } else if (powerToBeStarted < pMin) {
                startupGroup.setAvailablePower(pMin);
                powerToBeStarted -= pMin;
                startupGroup.setSetPointPower(pMin);
            } else {
                startupGroup.setSetPointPower(powerToBeStarted);
                marginalGroup = startupGroup;
                // if Mexico activated
                if (startupZone.getStartupType() == StartupType.EMPIL_ECO && startUpMarginalGroupType == StartupMarginalGroupType.MEXICO) {
                    addStartedPowerToTheRegion(startupZone, startupGroup);
                }
                startupZone.getStartedGroups().add(startupGroup);
                break;
            }
            startupZone.getStartedGroups().add(startupGroup);
            // prod per region if Mexico activated
            if (startupZone.getStartupType() == StartupType.EMPIL_ECO && startUpMarginalGroupType == StartupMarginalGroupType.MEXICO) {
                addStartedPowerToTheRegion(startupZone, startupGroup);
            }
        }

        // if Mexico activated
        if (startupZone.getStartupType() == StartupType.EMPIL_ECO && startUpMarginalGroupType == StartupMarginalGroupType.MEXICO && marginalGroup != null) {
            mexicoAdjustment(startupZone, marginalGroup);
        }
    }

    private void mexicoAdjustment(StartupZone startupZone, StartupGroup marginalGroup) {
        double epsilon = 0.005;
        double marginalCost = marginalGroup.getGenerator().getExtension(GeneratorStartup.class).getMarginalCost();

        //data for Mexico
        int nbRegions = startupZone.getRegions().size();
        List<Double> bco = new ArrayList<>(Collections.nCopies(nbRegions, 0.0));
        List<Double> xMax = new ArrayList<>(Collections.nCopies(nbRegions, 0.0));
        List<Double> xMin = new ArrayList<>(Collections.nCopies(nbRegions, 0.0));
        List<Double> xSol = new ArrayList<>(Collections.nCopies(nbRegions, 0.0));
        List<Integer> lBas = new ArrayList<>(Collections.nCopies(nbRegions, -1));
        double equilibrium = 0;

        //filling marginal groups
        for (StartupGroup startupGroup : startupZone.getStartupGroups()) {
            double startUpCostGroup = startupGroup.getGenerator().getExtension(GeneratorStartup.class).getStartUpCost();
            if (startUpCostGroup - marginalCost < -epsilon || startUpCostGroup - marginalCost > epsilon || !startupGroup.isUsable() || startupGroup.getAvailablePower() <= 0) {
                continue;
            }
            // add the group to its region marginal groups
            String regName = startupGroup.getGenerator().getTerminal().getBusView().getBus().getVoltageLevel().getSubstation().getProperty(REGION_CVG);
            if (regName == null) {
                regName = UNKNOWN_REGION;
            }
            startupZone.getRegions().get(regName).getMarginalGroups().add(startupGroup);

            int numReg = 0;

            for (StartupRegion startupRegion : startupZone.getRegions().values()) {
                double regionBalance = startupRegion.getRegionBalance();
                for (StartupGroup startupGroup1 : startupRegion.getMarginalGroups()) {
                    regionBalance -= startupGroup1.getAvailablePower();
                }
                xMax.set(numReg, startupRegion.getAvailablePower());
                xMin.set(numReg, 0.);
                xSol.set(numReg, 0.);
                lBas.set(numReg, -1);
                bco.set(numReg, 2 * regionBalance);
                numReg++;
                equilibrium -= startupRegion.getRegionBalance() - startupRegion.getStartedPower();
            }

            groups2Qua(nbRegions, bco, xMax, xMin, xSol, equilibrium, lBas);

        }
    }

    // Calculate zone production (imposed and available)
    // set each group as usable or not + startedPower (with which it can starts)
    // if Mexico calculate per region
    // this methods sort zone groups
    double evaluateProd(StartupZone startupZone) {
        final double[] pMaxAvailable = {0};
        for (StartupGroup startupGroup : startupZone.getStartupGroups()) {
            GeneratorStartup generatorStartupExtension = startupGroup.getGenerator().getExtension(GeneratorStartup.class);
            if (generatorStartupExtension.getPredefinedActivePowerSetpoint() != Double.MAX_VALUE) {
                // imposed power
                if (generatorStartupExtension.getPredefinedActivePowerSetpoint() >= 0) {
                    startupZone.setImposedPower(startupZone.getImposedPower() + generatorStartupExtension.getPredefinedActivePowerSetpoint());
                } else {
                    startupZone.setConsumption(startupZone.getConsumption() - generatorStartupExtension.getPredefinedActivePowerSetpoint());
                    // if Mexico activated
                    if (startupZone.getStartupType() == StartupType.EMPIL_ECO && startUpMarginalGroupType == StartupMarginalGroupType.MEXICO) {
                        addImposedPowerToTheRegionAsConsumption(startupZone, startupGroup, Math.abs(generatorStartupExtension.getPredefinedActivePowerSetpoint()));
                    }
                }
                startupGroup.setAvailablePower(0);
                startupGroup.setSetPointPower(generatorStartupExtension.getPredefinedActivePowerSetpoint());
                startupGroup.setImposed(true);
                startupGroup.setUsable(true);
                startupZone.getStartedGroups().add(startupGroup);
            } else {
                if (startupGroup.getGenerator().getEnergySource() == EnergySource.HYDRO) {
                    // do not start hydro groups !
                    startupGroup.setUsable(false);
                    continue;
                }
                double pMaxAvailableStartupGroup = evaluateAvailableMaxPower(startupGroup);
                pMaxAvailable[0] += pMaxAvailableStartupGroup;
                startupGroup.setAvailablePower(pMaxAvailableStartupGroup);
                startupGroup.setUsable(true);
            }
            // if Mexico activated
            if (startupZone.getStartupType() == StartupType.EMPIL_ECO && startUpMarginalGroupType == StartupMarginalGroupType.MEXICO) {
                addAvailablePowerToTheRegion(startupZone, startupGroup);
            }
        }
        return pMaxAvailable[0];
    }

    private void addStartedPowerToTheRegion(StartupZone startupZone, StartupGroup startupGroup) {
        String regName = startupGroup.getGenerator().getTerminal().getBusView().getBus().getVoltageLevel().getSubstation().getProperty(REGION_CVG);
        if (regName != null) {
            startupZone.getRegions().computeIfAbsent(regName, k -> new StartupRegion());
            double oldValue = startupZone.getRegions().get(regName).getStartedPower();
            startupZone.getRegions().get(regName).setStartedPower(oldValue + startupGroup.getAvailablePower());
        } else {
            double oldValue = startupZone.getRegions().get(UNKNOWN_REGION).getStartedPower();
            startupZone.getRegions().get(UNKNOWN_REGION).setStartedPower(oldValue + startupGroup.getSetPointPower());
        }
    }

    private void addAvailablePowerToTheRegion(StartupZone startupZone, StartupGroup startupGroup) {
        String regName = startupGroup.getGenerator().getTerminal().getBusView().getBus().getVoltageLevel().getSubstation().getProperty(REGION_CVG);
        if (regName != null) {
            startupZone.getRegions().computeIfAbsent(regName, k -> new StartupRegion());
            double oldValue = startupZone.getRegions().get(regName).getAvailablePower();
            startupZone.getRegions().get(regName).setAvailablePower(oldValue + startupGroup.getAvailablePower());
        } else {
            double oldValue = startupZone.getRegions().get(UNKNOWN_REGION).getAvailablePower();
            startupZone.getRegions().get(UNKNOWN_REGION).setAvailablePower(oldValue + startupGroup.getAvailablePower());
        }
    }

    private void addImposedPowerToTheRegionAsConsumption(StartupZone startupZone, StartupGroup startupGroup, double value) {
        String regName = startupGroup.getGenerator().getTerminal().getBusView().getBus().getVoltageLevel().getSubstation().getProperty(REGION_CVG);
        if (regName != null) {
            startupZone.getRegions().computeIfAbsent(regName, k -> new StartupRegion());
            double oldValue = startupZone.getRegions().get(regName).getConsumption();
            startupZone.getRegions().get(regName).setConsumption(oldValue + value);
        } else {
            double oldValue = startupZone.getRegions().get(UNKNOWN_REGION).getConsumption();
            startupZone.getRegions().get(UNKNOWN_REGION).setConsumption(oldValue + value);
        }
    }

    double evaluateAvailableMaxPower(StartupGroup startupGroup) {
        double pMaxAvailable = 0;
        // check if the group should started at pMax in parameters
        GeneratorStartup ext = startupGroup.getGenerator().getExtension(GeneratorStartup.class);
        double plannedOutageRate = ext != null ? ext.getPlannedOutageRate() : -1;
        double forcedOutageRate = ext != null ? ext.getForcedOutageRate() : -1;

        if (startupGroupsPowerMax.contains(startupGroup.getGenerator())) {
            pMaxAvailable = startupGroup.getGenerator().getMaxP();
        } else if (plannedOutageRate == -1 || forcedOutageRate == -1) {
            if (defaultAbatementCoefficient > 0 && defaultAbatementCoefficient < 1) {
                pMaxAvailable = startupGroup.getGenerator().getMaxP() * (1 - defaultAbatementCoefficient);
            } else {
                pMaxAvailable = startupGroup.getGenerator().getMaxP();
            }
        } else {
            pMaxAvailable  = startupGroup.getGenerator().getMaxP() * (1 - forcedOutageRate) * (1 - plannedOutageRate);
        }

        double abatementResFreqPercentage = resFreqCoefficient(startupGroup);
        pMaxAvailable *= 1 - abatementResFreqPercentage;
        return pMaxAvailable;
    }

    private double resFreqCoefficient(StartupGroup startupGroup) {
        // percentage to be kept for the frequency reserve
        double abatementResFreqPercentage = 0;

        switch (startupGroup.getGenerator().getEnergySource()) {
            case HYDRO: abatementResFreqPercentage = hydroBandSetting; break;
            case NUCLEAR: abatementResFreqPercentage = nuclearBandSetting; break;
            case THERMAL: abatementResFreqPercentage = thermalBandSetting; break;
            default: abatementResFreqPercentage = 0;
        }
        return abatementResFreqPercentage;
    }

    void groups2Qua(int n, List<Double> bco, List<Double> xMax, List<Double> xMin, List<Double> xSol, double equilibrium, List<Integer> lBas) {
        // MINIMISER LA SOMME , POUR I=1,N , DE :
        //
        // BCO(I)*(XSOL(I)-XMIN(I))+ACO(I)*(XSOL(I)-XMIN(I))²
        //
        // SOUS LA CONTRAINTE : SOMME DES XSOL(I)=EQUIL
        //
        // AVEC   XMIN(I) < XSOL(I) < XMAX(I)
        //
        // ALAG ET LRJ DE DIMENSION 2N , LBAS DE DIMENSION N
        //
        // On alimente ce module avec :
        // n le nombre de regions
        // Xmin =0; Xmax = Puissance marginale demarrable sur la region
        // equil = le volume a demarrer pour assurer l equilibre
        // bco = 2 * bilan importateur region
        // aco =1
        // Le probleme de minimisation revient a
        // minimiser la somme sur les regions :
        // ( bilan Import  + XSOL)^2

        List<Double> aco = new ArrayList<>(Collections.nCopies(n, 0.0));
        List<Double> alag = new ArrayList<>(Collections.nCopies(2 * n, 0.0));
        List<Integer> lrj = new ArrayList<>(Collections.nCopies(2 * n, 0));
        int nlb; // TODO
        double   eps1 = 1.e-5;
        double eps2 = 0.1;
        double  ss;
        double  sxm;
        double  xs;
        double  xm;
        double  ca;
        double  x;
        double  ag;
        double  agj;
        double  sso;
        double  r;
        int  i;
        int  j;
        int  ij;
        int  jj;
        int  nn;
        int  nm;
        int  km;
        int  jb;
        int  k;
        int  ih;

        // 1-  CALCULATE DES COEFFICIENTS ET INITIALISATIONS
        // ----------------------------------------------
        ss  = 0.;
        sxm = 0.;

        for (i = 0; i < n; i++) {
            xs = xMin.get(i);
            xm = xMax.get(i);
            xSol.set(i, xs);
            ss += xs;
            sxm += xm;
            lBas.set(i, -1);
            alag.set(i, bco.get(i));
            ca = aco.get(i) * 2;
            x = xm - xs;

            if (ca < eps1 || x < 0) {
                LOGGER.warn("ERREUR DANS groupes2Qua LA VARIABLE {}", i);
                LOGGER.warn("COEF {}", aco.get(i));
                LOGGER.warn("BORNE SUP {}", xMax.get(i));
                LOGGER.warn("BORNE MIN {}", xMin.get(i));
                return;
            }
            alag.set(n + i, bco.get(i) + ca * x);
            lrj .set(i, i);
            lrj.set(n + i, i);
        }
        // 2-  Y A-T-IL UNE SOLUTION ?
        if (ss > equilibrium || sxm < equilibrium) {
            LOGGER.warn("ERREUR DANS groupes2Qua");
            LOGGER.warn("DOMAINE DE DEFINITION {}", ss);
            LOGGER.warn("A {}", sxm);
            LOGGER.warn("CONTRAINTE {}", equilibrium);
            return;
        }
        // 3- CLASSEMENT
        // -------------
        nn = 2 * n;
        nm = nn - 1;
        for (i = 0; i < nm; i++) {
            xm = alag.get(i);
            km = i;
            jj = i + 1;
            for (j = jj; j < nn; j++) {
                if (alag.get(j) < xm) {
                    xm = alag.get(j);
                    km = j;
                }
            }
            alag.set(km, alag.get(i));
            alag.set(i, xm);
            ij = lrj.get(i);
            lrj.set(i, lrj.get(km));
            lrj.set(km, ij);
        }
        // 4- INDICE DE LA SOLUTION INITIALE
        // ---------------------------------
        sxm = ss;
        for (jb = 0; jb < nn; jb++) {
            i = lrj.get(jb);
            xm       = xMax.get(i) - xMin.get(i);
            lBas.set(i, lBas.get(i) + 1);
            if (lBas.get(i) != 0) {
                xSol.set(i, xMax.get(i));
                ss += xm;
            } else {
                sxm += xm;
            }
            if (sxm >= equilibrium) {
                // 6- SOLUTION INITIAL
                ag = alag.get(jb);
                for (i = 0; i < n; i++) {
                    if (lBas.get(i) == 0) {
                        x = (ag - bco.get(i)) / (2. * aco.get(i));
                        xSol.set(i, xSol.get(i) + x);
                        ss += x;
                    }
                }
                // 7- OPTIMISATION
                // ---------------
                for (j = jb + 1; j < nn; j++) {
                    sso = ss;
                    agj = alag.get(j);
                    xm = (agj - ag) / 2.;
                    if (xm >= eps1) {
                        for (k = 0; k < n; k++) {
                            if (lBas.get(k) == 0) {
                                r = xm / aco.get(k);
                                ss += r;
                                xSol.set(k, xSol.get(k) + r);
                            }
                        }
                        if (ss >= equilibrium) {
                            // 9- AJUSTEMENT DE LA SOLUTION
                            // ----------------------------
                            xm = xm * (equilibrium - ss) / (ss - sso);
                            for (k = 0; k < n; k++) {
                                if (lBas.get(k) == 0) {
                                    r = xm / aco.get(k);
                                    ss      += r;
                                    xSol.set(k, xSol.get(k) + r);
                                }
                            }
                            if (Math.abs(ss - equilibrium) > eps2) {
                                LOGGER.warn(DOUBTFUL_PRECISION);
                                LOGGER.warn(SS, ss);
                                LOGGER.warn(EQUILIBRIUM, equilibrium);
                                return;
                            }
                            // 10- CALCUL DU NOMBRE DE REGION DE BASE
                            // --------------------------------------
                            nlb = 0;
                            for (i = 0; i < n; i++) {
                                if (lBas.get(i) == 0) {
                                    nlb++;
                                }
                            }
                            return;
                        }
                    }
                    ih = lrj.get(j);
                    lBas.set(ih, lBas.get(ih) + 1);
                    ag = agj;
                }
                // 8- ERREUR ?
                if (Math.abs(ss - equilibrium) > eps2) {
                    LOGGER.warn(DOUBTFUL_PRECISION);
                    LOGGER.warn(SS, ss);
                    LOGGER.warn(EQUILIBRIUM, equilibrium);
                    return;
                }
                break;
            }
        }
        // 5- ERREUR ?
        LOGGER.warn(DOUBTFUL_PRECISION);
        LOGGER.warn(SS, ss);
        LOGGER.warn(EQUILIBRIUM, equilibrium);
    }
}


