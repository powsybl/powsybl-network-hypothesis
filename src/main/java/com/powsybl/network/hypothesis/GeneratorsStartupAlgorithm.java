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
            }
        });

        if (consFictitious[0] != 0) {
            LOGGER.info("Fictive injection : {} MW (added to the load)", consFictitious);
        }
        startupZone.setConsumption(consZone[0]);
    }

    private void economicalStacking(StartupZone startupZone) {
        double pMaxAvailable = 0;

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

        updateSetPointsPower(startupZone, powerToBeStarted);
    }

    private void updateSetPointsPower(StartupZone startupZone, double neededPower) {
        double powerToBeStarted = neededPower;

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
                startupZone.getStartedGroups().add(startupGroup);
                break;
            }
            startupZone.getStartedGroups().add(startupGroup);
        }
    }

    // calculate zone production (imposed and available)
    // set if a group is usable or not + set startedPower
    // this method sort zone groups
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
        }
        return pMaxAvailable[0];
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
}


