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

    // Parameters  for economical stacking startup
    List<StartupGroup> startupGroupsPowerMax = new ArrayList<>();

    double nuclearBandSetting = 0.05;
    double thermalBandSetting = 0.1;
    double hydroBandSetting = 0.1;
    double fictitiousBandSetting = 0;

    double lossCoefficient = 0;
    double defaultAbatementCoefficient = 0;

    StartupMarginalGroupType startUpMarginalGroupType = StartupMarginalGroupType.CLASSIC;  // startup algorithm used by the marginal generator

    //thresholds for voltage adjustment
    double pThreshold = 0.0;
    double qThreshold = 0.0;

    public void apply(Network network, StartupMarginalGroupType startUpMarginalGroupType, double defaultAbatementCoefficient,
                      double lossCoefficient, double pThreshold, double qThreshold) {

        if (startUpMarginalGroupType == StartupMarginalGroupType.MEXICO) {
            throw new GeneratorsStartupAlgorithmException("NOT SUPPORTED YET");
        }

        this.lossCoefficient = lossCoefficient;
        this.defaultAbatementCoefficient = defaultAbatementCoefficient;
        this.pThreshold = pThreshold;
        this.qThreshold = qThreshold;
        this.startUpMarginalGroupType = startUpMarginalGroupType;

        // prepare startupGroupsPerConnectedComponent map
        Map<Component, List<StartupGroup>> startupGroupsPerConnectedComponent = prepareStartupGroupsPerConnectedComponentMap(network);

        // for each connected component
        startupGroupsPerConnectedComponent.keySet().forEach(component -> {
            // create the zone for that connected component
            Zone zone = new Zone(component.getNum(), StartupType.EMPIL_ECO, true,  new ArrayList<>(), "Zone" + component.getNum(),
                    startupGroupsPerConnectedComponent.get(component), new ArrayList<>(), 0);
            // log component num
            LOGGER.debug("Component: {}", component.getNum());

            double consumption = evaluateConsumption(network, zone);

            if (zone.getStartupType() == StartupType.EMPIL_ECO) {
                economicalStacking(zone, consumption);
            }

            if (zone.isCanStart()) {
                final double[] startedProduction = {0};

                zone.getInitialGroups().forEach(startupGroup -> {
                    startupGroup.getGenerator().setTargetP(startupGroup.getStartValue());
                    startedProduction[0] += startupGroup.getStartValue();

                    if ((startupGroup.getGenerator().getTargetP() >= pThreshold) &&
                            (startupGroup.getGenerator().getReactiveLimits().getMaxQ(0) - startupGroup.getGenerator().getReactiveLimits().getMinQ(0) >= qThreshold)) {
                        startupGroup.getGenerator().setVoltageRegulatorOn(true);
                        LOGGER.info("Primary Voltage Control in operation");
                    }
                });

                if (Math.abs(startedProduction[0] - consumption) > 1)  {
                    LOGGER.error("Wrong starting production units: load + loss = {} MW; started production {} MW", consumption, startedProduction);
                }
            }
        });
    }

    private Map<Component, List<StartupGroup>> prepareStartupGroupsPerConnectedComponentMap(Network network) {
        Map<Component, List<StartupGroup>> startupGroupsPerConnectedComponent = new HashMap<>();
        network.getGeneratorStream().forEach(generator -> {
            StartupGroup startupGroup = new StartupGroup(false, 0, generator);
            Component component = generator.getRegulatingTerminal().getBusView().getConnectableBus().getConnectedComponent();
            startupGroupsPerConnectedComponent.computeIfAbsent(component, k -> new ArrayList<>());
            List<StartupGroup> l = startupGroupsPerConnectedComponent.get(component);
            l.add(startupGroup);
            startupGroupsPerConnectedComponent.put(component, l);
        });
        return startupGroupsPerConnectedComponent;
    }

    private double evaluateConsumption(Network network, Zone zone) {
        final double[] consZone = {0};
        final double[] consFictitious = {0};

        network.getLoadStream().forEach(load -> {
            if (load.getTerminal().getBusView().getBus().getConnectedComponent().getNum() == zone.getNum()) {
                consZone[0] += load.getP0() * (1 + lossCoefficient);
                if (load.getLoadType() == LoadType.FICTITIOUS) {
                    consFictitious[0] += load.getP0() * (1 + lossCoefficient);
                }
            }
        });

        if (consFictitious[0] != 0) {
            LOGGER.info("Fictive injection : {} MW (added to the load)", consFictitious);
        }

        return consZone[0];
    }

    private void economicalStacking(Zone zone, double consumption) {
        double pMaxAvailable = 0;

        // Evaluate available production
        pMaxAvailable = evaluateProd(zone);

        // secondary connected component without neither imposed production nor consumption
        if (zone.getNum() != 0 && Math.abs(zone.getImposedPower()) < 1. && Math.abs(consumption) < 1) {
            zone.setCanStart(false);
            return;
        }

        //Verify that we can start groups
        if (zone.getImposedPower() + pMaxAvailable < consumption) {
            LOGGER.error("Starting production units impossible on area {} : too much imposed power or lack of production available", zone.getName());
            if (zone.getNum() == 0) {
                LOGGER.error("Principal connected component non treated");
            } else {
                LOGGER.error("Secondary connected component non treated");
            }
            zone.setCanStart(false);
        }

        // economical stacking :
        zone.getInitialGroups().sort(new StartupGroupComparator());

        double powerToBeStarted = consumption - zone.getImposedPower();
        LOGGER.info("Power to be started {}", powerToBeStarted);

        for (StartupGroup startupGroup : zone.getInitialGroups()) {
            if (!startupGroup.isUsable()) {
                LOGGER.error("Not enough production units available");
                zone.setCanStart(false);
                return;
            }

            double pMin = startupGroup.getGenerator().getMinP() < 0 ? startupGroup.getGenerator().getMinP() : 0;

            if (startupGroup.getGenerator().getMaxP() < powerToBeStarted) {
                startupGroup.setStartValue(startupGroup.getGenerator().getMaxP());
                powerToBeStarted -= startupGroup.getGenerator().getMaxP();
            } else if (powerToBeStarted < pMin) {
                startupGroup.setStartValue(pMin);
                powerToBeStarted -= pMin;
            } else {
                startupGroup.setStartValue(powerToBeStarted);
                break;
            }
        }
    }

    double evaluateProd(Zone zone) {
        final double[] pMaxAvailable = {0};
        zone.getInitialGroups().forEach(startupGroup -> {
            GeneratorStartup generatorStartup = startupGroup.getGenerator().getExtension(GeneratorStartup.class);
            if (generatorStartup.getPredefinedActivePowerSetpoint() > 0) {
                zone.setImposedPower(zone.getImposedPower() + generatorStartup.getPredefinedActivePowerSetpoint());
                startupGroup.setStartValue(generatorStartup.getPredefinedActivePowerSetpoint());
                startupGroup.setUsable(false);
            } else {
                double pMaxAvailableStartupGroup = evaluateAvailableMaxPower(startupGroup);
                pMaxAvailable[0] += pMaxAvailableStartupGroup;
                startupGroup.setStartValue(pMaxAvailableStartupGroup);
                startupGroup.setUsable(true);
            }
            zone.getStartedGroups().add(startupGroup);
        });
        return pMaxAvailable[0];
    }

    double evaluateAvailableMaxPower(StartupGroup startupGroup) {
        double pMaxAvailable = 0;
        // check if the group should started at pMax in parameters
        GeneratorStartup ext = startupGroup.getGenerator().getExtension(GeneratorStartup.class);
        double plannedOutageRate = ext != null ? ext.getPlannedOutageRate() : -1;
        double forcedOutageRate = ext != null ? ext.getForcedOutageRate() : -1;

        if (startupGroupsPowerMax.contains(startupGroup)) {
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
            case OTHER: abatementResFreqPercentage = fictitiousBandSetting; break;
            default: abatementResFreqPercentage = 0;
        }
        return abatementResFreqPercentage;
    }
}


