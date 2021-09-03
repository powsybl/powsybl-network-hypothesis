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

    double nuclearAdequacyMarginRatio = 0.05; // the ratio of generation reserved for adequacy for nuclear units
    double thermalAdequacyMarginRatio = 0.05; // the ratio of generation reserved for adequacy for thermal units
    double hydroAdequacyMarginRatio = 0.1; // the ratio of generation reserved for adequacy for hydro units

    double lossFactor = 0; // the factor of active power losses in the whole network
    double defaultReductionRatio = 0; // default ratio that defines the global reduction of active power availability

    StartupMarginalGeneratorType startupMarginalGeneratorType = StartupMarginalGeneratorType.BASIC;

    // thresholds for voltage control
    double pThreshold = 0.0;
    double qThreshold = 0.0;

    /**
     * starts generators groups using Classic or Mexico algorithm
     * @param network
     * @param startupMarginalGeneratorType BASIC only for the moment
     * @param defaultReductionRatio default ratio that defines the global reduction of active power availability
     * @param lossFactor the coefficient of active power losses
     * @param pThreshold pThreshold parameter
     * @param qThreshold qThreshold parameter
     * @param startupGroupsAtMaxActivePower list of generators that should be started at maxP if chosen
     */
    public void apply(Network network, StartupMarginalGeneratorType startupMarginalGeneratorType, double defaultReductionRatio,
                      double lossFactor, double pThreshold, double qThreshold, List<Generator> startupGroupsAtMaxActivePower) {
        //parameters initialization
        this.lossFactor = lossFactor;
        this.defaultReductionRatio = defaultReductionRatio;
        this.pThreshold = pThreshold;
        this.qThreshold = qThreshold;
        this.startupMarginalGeneratorType = startupMarginalGeneratorType;
        this.startupGeneratorsAtMaxActivePower = startupGroupsAtMaxActivePower;

        // prepare startupGroupsPerConnectedComponent HashMap
        Map<Component, List<StartupGenerator>> startupGroupsPerConnectedComponent = prepareStartupGroupsPerConnectedComponent(network);

        // for each connected component
        startupGroupsPerConnectedComponent.keySet().forEach(component -> {
            // create the startupArea for that connected component
            StartupArea startupArea = StartupArea.builder()
                    .name("StartupZone" + component.getNum())
                    .num(component.getNum())
                    .startupType(StartupType.PRECEDENCE_ECONOMIC)
                    .isActive(true)
                    .countries(new ArrayList<>())
                    .startupGroups(startupGroupsPerConnectedComponent.get(component))
                    .startedGroups(new ArrayList<>())
                    .plannedActivePower(0)
                    .build();

            LOGGER.debug("Component: {}", component.getNum());

            evaluateConsumption(network, startupArea); // active power consumption and losses

            if (startupArea.getStartupType() == StartupType.PRECEDENCE_ECONOMIC) {
                economicPrecedence(startupArea);
            }

            if (startupArea.isActive()) {
                final double[] plannedGeneration = {0};

                startupArea.getStartedGroups().forEach(startupGenerator -> {
                    startupGenerator.getGenerator().setTargetP(startupGenerator.getActivePowerSetpoint());
                    plannedGeneration[0] += startupGenerator.getActivePowerSetpoint();

                    if ((startupGenerator.getGenerator().getTargetP() >= pThreshold) &&
                            (startupGenerator.getGenerator().getReactiveLimits().getMaxQ(0) - startupGenerator.getGenerator().getReactiveLimits().getMinQ(0) >= qThreshold)) {
                        startupGenerator.getGenerator().setVoltageRegulatorOn(true);
                        LOGGER.info("Voltage control on for generator {}", startupGenerator.getGenerator().getId());
                    }
                });

                if (Math.abs(plannedGeneration[0] - startupArea.getTotalConsumption()) > 1)  {
                    LOGGER.error("Wrong planned generation for area {}: consumption + loss = {} MW; planned generation {} MW", startupArea.getName(),
                            startupArea.getTotalConsumption(), plannedGeneration);
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

    // compute area total consumption and total active power losses
    private void evaluateConsumption(Network network, StartupArea startupArea) {
        final double[] areaConsumption = {0};
        final double[] areaFictitiousConsumption = {0};

        network.getLoadStream().forEach(load -> {
            if (load.getTerminal().getBusView().getBus().getConnectedComponent().getNum() == startupArea.getNum()) {
                areaConsumption[0] += load.getP0() * (1 + lossFactor);
                if (load.getLoadType() == LoadType.FICTITIOUS) {
                    areaFictitiousConsumption[0] += load.getP0() * (1 + lossFactor);
                }
            }
        });

        if (areaFictitiousConsumption[0] != 0) {
            LOGGER.info("Area fictitious consumption : {} MW (added to the area consumption)", areaFictitiousConsumption);
        }
        startupArea.setTotalConsumption(areaConsumption[0]);
    }

    private void economicPrecedence(StartupArea startupArea) {
        // evaluate available production
        double pMaxAvailable = evaluateGeneration(startupArea);

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

        double requiredActivePower = startupArea.getTotalConsumption() - startupArea.getTotalPlannedActivePower();
        LOGGER.info("Required active power generation of {} MW for area {}", requiredActivePower, startupArea.getName());

        updateGeneratorActivePowerSetpoints(startupArea, requiredActivePower);
    }

    private void updateGeneratorActivePowerSetpoints(StartupArea startupArea, double requiredActivePower) {
        double activePowerToBeStarted = requiredActivePower;

        for (StartupGenerator startupGenerator : startupArea.getStartupGenerators()) {
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

    // calculate area generation (planned and available)
    // set if a group is available or not and set startedPower (?)
    double evaluateGeneration(StartupArea startupArea) {
        final double[] pMaxAvailable = {0};
        for (StartupGenerator startupGenerator : startupArea.getStartupGenerators()) {
            GeneratorStartup generatorStartupExtension = startupGenerator.getGenerator().getExtension(GeneratorStartup.class);
            if (generatorStartupExtension != null && generatorStartupExtension.getPlannedActivePowerSetpoint() != 0) {
                // compute the total planned generation
                if (generatorStartupExtension.getPlannedActivePowerSetpoint() >= 0) {
                    startupArea.setTotalPlannedActivePower(startupArea.getTotalPlannedActivePower() + generatorStartupExtension.getPlannedActivePowerSetpoint());
                } else {
                    // if the generator has a negative active power set point, it is added to total consumption
                    startupArea.setTotalConsumption(startupArea.getTotalConsumption() - generatorStartupExtension.getPlannedActivePowerSetpoint());
                }
                startupGenerator.setAvailableActivePower(0); // cannot be started because already started
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

        if (startupGeneratorsAtMaxActivePower.contains(startupGenerator.getGenerator())) {
            pMaxAvailable = startupGenerator.getGenerator().getMaxP();
        } else if (plannedOutageRate == -1 || forcedOutageRate == -1) {
            if (Math.abs(defaultReductionRatio) < 1) {
                pMaxAvailable = startupGenerator.getGenerator().getMaxP() * (1 - defaultReductionRatio);
            } else {
                pMaxAvailable = startupGenerator.getGenerator().getMaxP();
            }
        } else {
            pMaxAvailable  = startupGenerator.getGenerator().getMaxP() * (1 - forcedOutageRate) * (1 - plannedOutageRate);
        }

        double adequacyRatio = computeAdequacyMarginRatio(startupGenerator);
        pMaxAvailable *= 1 - adequacyRatio;
        return pMaxAvailable;
    }

    private double computeAdequacyMarginRatio(StartupGenerator startupGenerator) {
        // ratio of active generation to be kept for frequency reserve.
        double generatorAdequacyMarginRatio = 0;

        switch (startupGenerator.getGenerator().getEnergySource()) {
            case HYDRO: generatorAdequacyMarginRatio = hydroAdequacyMarginRatio; break;
            case NUCLEAR: generatorAdequacyMarginRatio = nuclearAdequacyMarginRatio; break;
            case THERMAL: generatorAdequacyMarginRatio = thermalAdequacyMarginRatio; break;
            default: generatorAdequacyMarginRatio = 0;
        }
        return generatorAdequacyMarginRatio;
    }
}


