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
public class GeneratorsStartupHypothesis {
    private static final Logger LOGGER = LoggerFactory.getLogger(GeneratorsStartupHypothesis.class);

    List<Generator> startupGeneratorsAtMaxActivePower = new ArrayList<>(); // list of generators that should be started at maxP if chosen

    double lossFactor = 0; // the factor of active power losses in the whole network

    StartupMarginalGeneratorType startupMarginalGeneratorType = StartupMarginalGeneratorType.BASIC;

    // thresholds for voltage control
    double pThreshold = 0.0;
    double qThreshold = 0.0;

    /**
     * starts generators groups using Classic or Mexico algorithm
     * @param network
     * @param startupMarginalGeneratorType BASIC only for the moment
     * @param lossFactor the coefficient of active power losses
     * @param pThreshold pThreshold parameter
     * @param qThreshold qThreshold parameter
     * @param startupGroupsAtMaxActivePower list of generators that should be started at maxP if chosen
     */
    public void apply(Network network, StartupMarginalGeneratorType startupMarginalGeneratorType, double lossFactor,
                      double pThreshold, double qThreshold, List<Generator> startupGroupsAtMaxActivePower) {
        //parameters initialization
        this.lossFactor = lossFactor;
        this.pThreshold = pThreshold;
        this.qThreshold = qThreshold;
        this.startupMarginalGeneratorType = startupMarginalGeneratorType;
        this.startupGeneratorsAtMaxActivePower = startupGroupsAtMaxActivePower;

        // prepare startupGroupsPerConnectedComponent HashMap
        Map<Component, List<GeneratorState>> startupGroupsPerConnectedComponent = prepareStartupGroupsPerConnectedComponent(network);

        // for each connected component
        startupGroupsPerConnectedComponent.keySet().forEach(component -> {
            // create the startupArea for that connected component
            StartupArea startupArea = StartupArea.builder()
                    .name("StartupZone" + component.getNum())
                    .num(component.getNum())
                    .startupType(StartupType.ECONOMIC_PRECEDENCE)
                    .isActive(true)
                    .countries(new ArrayList<>())
                    .startupGroups(startupGroupsPerConnectedComponent.get(component))
                    .startedGroups(new ArrayList<>())
                    .plannedActivePower(0)
                    .build();

            LOGGER.debug("Component: {}", component.getNum());

            startupArea.evaluateConsumption(network, lossFactor); // active power consumption and losses

            if (startupArea.getStartupType() == StartupType.ECONOMIC_PRECEDENCE) {
                economicPrecedence(startupArea);
            }

            if (startupArea.isActive()) {
                final double[] plannedGeneration = {0};

                startupArea.getStartedGroups().forEach(startupGenerator -> {
                    startupGenerator.getGenerator().setTargetP(startupGenerator.getActivePowerSetpoint());
                    LOGGER.info("Generator {} with active power setpoint {}", startupGenerator.getGenerator().getId(), startupGenerator.getAvailableActivePower());
                    plannedGeneration[0] += startupGenerator.getActivePowerSetpoint();

                    if ((startupGenerator.getGenerator().getTargetP() >= pThreshold) &&
                            (startupGenerator.getGenerator().getReactiveLimits().getMaxQ(0) - startupGenerator.getGenerator().getReactiveLimits().getMinQ(0) >= qThreshold)) {
                        startupGenerator.getGenerator().setVoltageRegulatorOn(true);
                        LOGGER.info("Voltage control on for generator {}", startupGenerator.getGenerator().getId());
                    }
                });

                if (Math.abs(plannedGeneration[0] - startupArea.getTotalConsumption()) > 1)  {
                    LOGGER.error("Wrong planned generation for area {}: consumption + loss = {} MW; planned generation {} MW", startupArea.getName(),
                            startupArea.getTotalConsumption(), plannedGeneration[0]);
                }
            }
        });
    }

    private Map<Component, List<GeneratorState>> prepareStartupGroupsPerConnectedComponent(Network network) {
        Map<Component, List<GeneratorState>> startupGroupsPerConnectedComponent = new HashMap<>();
        network.getGeneratorStream().forEach(generator -> {
            GeneratorState startupGenerator = new GeneratorState(false, 0, 0, false, generator);
            Component component = generator.getRegulatingTerminal().getBusBreakerView().getConnectableBus().getConnectedComponent();
            startupGroupsPerConnectedComponent.computeIfAbsent(component, k -> new ArrayList<>());
            List<GeneratorState> l = startupGroupsPerConnectedComponent.get(component);
            l.add(startupGenerator);
        });
        return startupGroupsPerConnectedComponent;
    }

    private void economicPrecedence(StartupArea startupArea) {
        // evaluate available production
        double pMaxAvailable = startupArea.evaluateGeneration(startupGeneratorsAtMaxActivePower);

        // not main connected component without neither planned production or consumption
        if (startupArea.getNum() != 0 && Math.abs(startupArea.getTotalPlannedActivePower()) < 1. && Math.abs(startupArea.getTotalConsumption()) < 1) {
            startupArea.setActive(false);
            return;
        }

        // verify that we can start groups
        if (startupArea.getTotalPlannedActivePower() + pMaxAvailable < startupArea.getTotalConsumption()) {
            LOGGER.error("Starting generators on area {} is not possible: too much planned active power compared to total available generation", startupArea.getName());
            if (startupArea.getNum() == 0) {
                LOGGER.error("Main connected component not treated");
            } else {
                LOGGER.error("Connected component {} not treated", startupArea.getNum());
            }
            startupArea.setActive(false);
            return;
        }

        // economic precedence
        startupArea.getStartupGenerators().sort(new StartupGeneratorComparator());
        LOGGER.error("Total consumption for area {}: {}", startupArea.getNum(), startupArea.getTotalConsumption());
        LOGGER.error("Total planned active power for area {}: {}", startupArea.getNum(), startupArea.getTotalPlannedActivePower());
        double requiredActivePower = startupArea.getTotalConsumption() - startupArea.getTotalPlannedActivePower();
        LOGGER.info("Required active power generation of {} MW for area {}", requiredActivePower, startupArea.getName());

        updateGeneratorActivePowerSetpoints(startupArea, requiredActivePower);
    }

    private void updateGeneratorActivePowerSetpoints(StartupArea startupArea, double requiredActivePower) {
        double activePowerToBeStarted = requiredActivePower;

        for (GeneratorState startupGenerator : startupArea.getStartupGenerators()) {
            if (!startupGenerator.isAvailable()) {
                LOGGER.error("Generator {} is not available", startupGenerator.getGenerator().getNameOrId());
            }

            // if the generator has to be started at maximum available active power
            if (startupGeneratorsAtMaxActivePower.contains(startupGenerator.getGenerator()) && !startupGenerator.isPlanned()) {
                startupGenerator.setActivePowerSetpoint(startupGenerator.getAvailableActivePower());
                activePowerToBeStarted -= startupGenerator.getAvailableActivePower();
                startupArea.getStartedGroups().add(startupGenerator);
                continue;
            }

            // if the generator is not planned
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
}


