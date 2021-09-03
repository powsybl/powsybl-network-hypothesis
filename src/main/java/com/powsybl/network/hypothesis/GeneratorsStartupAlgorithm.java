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

    List<Generator> startupGroupsAtMaxActivePower = new ArrayList<>();

    double nuclearBandSetting = 0.05;
    double thermalBandSetting = 0.05;
    double hydroBandSetting = 0.1;

    double lossCoefficient = 0;
    double defaultAbatementCoefficient = 0;

    StartupMarginalGeneratorType startUpMarginalGeneratorType = StartupMarginalGeneratorType.BASIC;

    //thresholds for voltage adjustment
    double pThreshold = 0.0;
    double qThreshold = 0.0;

    /**
     * starts generators groups using Classic or Mexico algorithm
     * @param network
     * @param startUpMarginalGeneratorType Mexico or Classic
     * @param defaultAbatementCoefficient defaultAbatementCoefficient parameter
     * @param lossCoefficient lossCoefficient parameter
     * @param pThreshold pThreshold parameter
     * @param qThreshold qThreshold parameter
     * @param startupGroupsAtMaxActivePower list of groups that should be started at Pmax if picked
     */
    public void apply(Network network, StartupMarginalGeneratorType startUpMarginalGeneratorType, double defaultAbatementCoefficient,
                      double lossCoefficient, double pThreshold, double qThreshold, List<Generator> startupGroupsAtMaxActivePower) {
        //parameters initialization
        this.lossCoefficient = lossCoefficient;
        this.defaultAbatementCoefficient = defaultAbatementCoefficient;
        this.pThreshold = pThreshold;
        this.qThreshold = qThreshold;
        this.startUpMarginalGeneratorType = startUpMarginalGeneratorType;
        this.startupGroupsAtMaxActivePower = startupGroupsAtMaxActivePower;

        // prepare startupGroupsPerConnectedComponent HashMap
        Map<Component, List<StartupGenerator>> startupGroupsPerConnectedComponent = prepareStartupGroupsPerConnectedComponent(network);

        // for each connected component
        startupGroupsPerConnectedComponent.keySet().forEach(component -> {
            // create the startupZone for that connected component
            StartupArea startupArea = StartupArea.builder()
                    .name("StartupZone" + component.getNum())
                    .num(component.getNum())
                    .startupType(StartupType.PRECEDENCE_ECONOMIC)
                    .canStart(true)
                    .countries(new ArrayList<>())
                    .startupGroups(startupGroupsPerConnectedComponent.get(component))
                    .startedGroups(new ArrayList<>())
                    .plannedActivePower(0)
                    .build();

            // log component num
            LOGGER.debug("Component: {}", component.getNum());

            evaluateConsumption(network, startupArea); // real consumption + losses

            if (startupArea.getStartupType() == StartupType.PRECEDENCE_ECONOMIC) {
                economicalStacking(startupArea);
            }

            if (startupArea.isCanStart()) {
                final double[] startedProduction = {0};

                startupArea.getStartedGroups().forEach(startupGroup -> {
                    startupGroup.getGenerator().setTargetP(startupGroup.getActivePowerSetpoint());
                    startedProduction[0] += startupGroup.getActivePowerSetpoint();

                    if ((startupGroup.getGenerator().getTargetP() >= pThreshold) &&
                            (startupGroup.getGenerator().getReactiveLimits().getMaxQ(0) - startupGroup.getGenerator().getReactiveLimits().getMinQ(0) >= qThreshold)) {
                        startupGroup.getGenerator().setVoltageRegulatorOn(true);
                        LOGGER.info("Primary Voltage Control in operation");
                    }
                });

                if (Math.abs(startedProduction[0] - startupArea.getTotalConsumption()) > 1)  {
                    LOGGER.error("Wrong starting production units: load + loss = {} MW; started production {} MW", startupArea.getTotalConsumption(), startedProduction);
                }
            }
        });
    }

    private Map<Component, List<StartupGenerator>> prepareStartupGroupsPerConnectedComponent(Network network) {
        Map<Component, List<StartupGenerator>> startupGroupsPerConnectedComponent = new HashMap<>();
        network.getGeneratorStream().forEach(generator -> {
            StartupGenerator startupGenerator = new StartupGenerator(false, 0, 0, false, generator);
            Component component = generator.getRegulatingTerminal().getBusBreakerView().getConnectableBus().getConnectedComponent();
            startupGroupsPerConnectedComponent.computeIfAbsent(component, k -> new ArrayList<>());
            List<StartupGenerator> l = startupGroupsPerConnectedComponent.get(component);
            l.add(startupGenerator);
        });
        return startupGroupsPerConnectedComponent;
    }

    // compute area total consumption and active power losses
    private void evaluateConsumption(Network network, StartupArea startupArea) {
        final double[] areaConsumption = {0};
        final double[] areaFictitiousConsumption = {0};

        network.getLoadStream().forEach(load -> {
            if (load.getTerminal().getBusView().getBus().getConnectedComponent().getNum() == startupArea.getNum()) {
                areaConsumption[0] += load.getP0() * (1 + lossCoefficient);
                if (load.getLoadType() == LoadType.FICTITIOUS) {
                    areaFictitiousConsumption[0] += load.getP0() * (1 + lossCoefficient);
                }
            }
        });

        if (areaFictitiousConsumption[0] != 0) {
            LOGGER.info("Area fictitious consumption : {} MW (added to the area consumption)", areaFictitiousConsumption);
        }
        startupArea.setTotalConsumption(areaConsumption[0]);
    }

    private void economicalStacking(StartupArea startupArea) {
        double pMaxAvailable = 0;

        // Evaluate available production
        pMaxAvailable = evaluateGeneration(startupArea);

        // secondary connected component without neither imposed production nor consumption
        if (startupArea.getNum() != 0 && Math.abs(startupArea.getTotalPlannedActivePower()) < 1. && Math.abs(startupArea.getTotalConsumption()) < 1) {
            startupArea.setCanStart(false);
            return;
        }

        // verify that we can start groups
        if (startupArea.getTotalPlannedActivePower() + pMaxAvailable < startupArea.getTotalConsumption()) {
            LOGGER.error("Starting production units impossible on area {} : too much imposed power or lack of production available", startupArea.getName());
            if (startupArea.getNum() == 0) {
                LOGGER.error("Principal connected component not treated");
            } else {
                LOGGER.error("Secondary connected component not treated");
            }
            startupArea.setCanStart(false);
            return;
        }

        // economical stacking :
        startupArea.getStartupGenerators().sort(new StartupGeneratorComparator());

        double powerToBeStarted = startupArea.getTotalConsumption() - startupArea.getTotalPlannedActivePower();
        LOGGER.info("Power to be started {}", powerToBeStarted);

        updateGeneratorActivePowerSetpoints(startupArea, powerToBeStarted);
    }

    private void updateGeneratorActivePowerSetpoints(StartupArea startupArea, double requiredActivePower) {
        double activePowerToBeStarted = requiredActivePower;

        for (StartupGenerator startupGenerator : startupArea.getStartupGenerators()) {
            if (!startupGenerator.isAvailable()) {
                LOGGER.error("Generator {} is not available", startupGenerator.getGenerator().getNameOrId());
            }

            if (startupGroupsAtMaxActivePower.contains(startupGenerator.getGenerator()) && !startupGenerator.isPlanned()) {
                startupGenerator.setActivePowerSetpoint(startupGenerator.getAvailableActivePower());
                activePowerToBeStarted -= startupGenerator.getAvailableActivePower();
                startupArea.getStartedGroups().add(startupGenerator);
                continue;
            }

            double pMin = startupGenerator.getGenerator().getMinP() < 0 ? startupGenerator.getGenerator().getMinP() : 0;

            if (!startupGenerator.isPlanned()) {
                if (startupGenerator.getAvailableActivePower() < activePowerToBeStarted) {
                    activePowerToBeStarted -= startupGenerator.getAvailableActivePower();
                    startupGenerator.setActivePowerSetpoint(startupGenerator.getAvailableActivePower());
                } else if (activePowerToBeStarted < pMin) {
                    startupGenerator.setAvailableActivePower(pMin);
                    activePowerToBeStarted -= pMin;
                    startupGenerator.setActivePowerSetpoint(pMin);
                } else {
                    startupGenerator.setActivePowerSetpoint(activePowerToBeStarted);
                }
                startupArea.getStartedGroups().add(startupGenerator);
            }
        }
    }

    // calculate area generation (planned and available)
    // set if a group is available or not and set startedPower (?)
    double evaluateGeneration(StartupArea startupArea) {
        final double[] pMaxAvailable = {0};
        for (StartupGenerator startupGenerator : startupArea.getStartupGenerators()) {
            GeneratorStartup generatorStartupExtension = startupGenerator.getGenerator().getExtension(GeneratorStartup.class);
            if (generatorStartupExtension != null && generatorStartupExtension.getPlannedActivePowerSetpoint() != Double.MAX_VALUE) {
                // compute the total planned generation
                if (generatorStartupExtension.getPlannedActivePowerSetpoint() >= 0) {
                    startupArea.setTotalPlannedActivePower(startupArea.getTotalPlannedActivePower() + generatorStartupExtension.getPlannedActivePowerSetpoint());
                } else {
                    // if the generator has a negative active power set point, it is added to total consumption
                    startupArea.setTotalConsumption(startupArea.getTotalConsumption() - generatorStartupExtension.getPlannedActivePowerSetpoint());
                }
                startupGenerator.setAvailableActivePower(0); //FIXME: why?
                startupGenerator.setActivePowerSetpoint(generatorStartupExtension.getPlannedActivePowerSetpoint());
                startupGenerator.setPlanned(true);
                startupGenerator.setAvailable(true);
                startupArea.getStartedGroups().add(startupGenerator);
            } else {
                if (startupGenerator.getGenerator().getEnergySource() == EnergySource.HYDRO || generatorStartupExtension == null) {
                    // FIXME: should we ignore generators with no GeneratorStartup extension?
                    // we do not start hydro generator
                    startupGenerator.setAvailable(false);
                    continue;
                }
                double generatorPMaxAvailable = evaluatePMaxAvailable(startupGenerator);
                pMaxAvailable[0] += generatorPMaxAvailable;
                startupGenerator.setAvailableActivePower(generatorPMaxAvailable);
                startupGenerator.setAvailable(true);
            }
        }
        return pMaxAvailable[0];
    }

    double evaluatePMaxAvailable(StartupGenerator startupGenerator) {
        double pMaxAvailable = 0; //FIXME
        // check if the group should started at pMax in parameters
        GeneratorStartup ext = startupGenerator.getGenerator().getExtension(GeneratorStartup.class);
        double plannedOutageRate = ext != null ? ext.getPlannedOutageRate() : -1; // FIXME (should be 0?)
        double forcedOutageRate = ext != null ? ext.getForcedOutageRate() : -1; // FIXME (should be 0?)

        if (startupGroupsAtMaxActivePower.contains(startupGenerator.getGenerator())) {
            pMaxAvailable = startupGenerator.getGenerator().getMaxP();
        } else if (plannedOutageRate == -1 || forcedOutageRate == -1) {
            if (Math.abs(defaultAbatementCoefficient) < 1) {
                pMaxAvailable = startupGenerator.getGenerator().getMaxP() * (1 - defaultAbatementCoefficient);
            } else {
                pMaxAvailable = startupGenerator.getGenerator().getMaxP();
            }
        } else {
            pMaxAvailable  = startupGenerator.getGenerator().getMaxP() * (1 - forcedOutageRate) * (1 - plannedOutageRate);
        }

        double adequacyRatio = computeAdequacyRatio(startupGenerator);
        pMaxAvailable *= 1 - adequacyRatio;
        return pMaxAvailable;
    }

    private double computeAdequacyRatio(StartupGenerator startupGenerator) {
        // ratio of active generation to be kept for frequency reserve.
        double generatorAdequacyRatio = 0;

        switch (startupGenerator.getGenerator().getEnergySource()) {
            case HYDRO: generatorAdequacyRatio = hydroBandSetting; break;
            case NUCLEAR: generatorAdequacyRatio = nuclearBandSetting; break;
            case THERMAL: generatorAdequacyRatio = thermalBandSetting; break;
            default: generatorAdequacyRatio = 0;
        }
        return generatorAdequacyRatio;
    }
}


