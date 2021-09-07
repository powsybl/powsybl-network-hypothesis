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

import java.util.ArrayList;
import java.util.List;

/**
 * @author Chamseddine BENHAMED <chamseddine.benhamed at rte-france.com>
 */
public class StartupArea {
    private static final Logger LOGGER = LoggerFactory.getLogger(StartupArea.class);
    private String name;
    private int num;
    private StartupType startupType = StartupType.ECONOMIC_PRECEDENCE;
    private boolean active = true;
    private List<Country> countries = new ArrayList<>(); // This attribute is not used for the first version
    private List<GeneratorState> startupGenerators = new ArrayList<>(); // groupsToBeStarted?
    private List<GeneratorState> startedGroups = new ArrayList<>();
    private double totalPlannedActivePower = 0;
    private double totalConsumption = 0;
    double defaultReductionRatio = 0.1; // default ratio that defines the global reduction of active power availability

    public static StartupAreaBuilder builder() {
        return new StartupAreaBuilder();
    }

    public StartupArea(String name, int num, StartupType startupType, boolean active, List<Country> countries,
                       List<GeneratorState> startupGenerators, List<GeneratorState> startedGroups, double totalPlannedActivePower, double totalConsumption) {
        this.name = name;
        this.num = num;
        this.startupType = startupType;
        this.active = active;
        this.countries = countries;
        this.startupGenerators = startupGenerators;
        this.startedGroups = startedGroups;
        this.totalPlannedActivePower = totalPlannedActivePower;
        this.totalConsumption = totalConsumption;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getNum() {
        return num;
    }

    public void setNum(int num) {
        this.num = num;
    }

    public StartupType getStartupType() {
        return startupType;
    }

    public void setStartupType(StartupType startupType) {
        this.startupType = startupType;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public List<Country> getCountries() {
        return countries;
    }

    public void setCountries(List<Country> countries) {
        this.countries = countries;
    }

    public List<GeneratorState> getStartupGenerators() {
        return startupGenerators;
    }

    public void setStartupGenerators(List<GeneratorState> startupGenerators) {
        this.startupGenerators = startupGenerators;
    }

    public List<GeneratorState> getStartedGroups() {
        return startedGroups;
    }

    public void setStartedGroups(List<GeneratorState> startedGroups) {
        this.startedGroups = startedGroups;
    }

    public double getTotalPlannedActivePower() {
        return totalPlannedActivePower;
    }

    public void setTotalPlannedActivePower(double totalPlannedActivePower) {
        this.totalPlannedActivePower = totalPlannedActivePower;
    }

    public double getTotalConsumption() {
        return totalConsumption;
    }

    public void setTotalConsumption(double totalConsumption) {
        this.totalConsumption = totalConsumption;
    }

    public void evaluateConsumption(Network network, double lossFactor) {
        // compute area total consumption and total active power losses
        final double[] areaConsumption = {0};
        final double[] areaFictitiousConsumption = {0};

        network.getLoadStream().forEach(load -> {
            if (load.getTerminal().getBusView().getBus().getConnectedComponent().getNum() == this.getNum()) {
                areaConsumption[0] += load.getP0() * (1 + lossFactor);
                if (load.getLoadType() == LoadType.FICTITIOUS) {
                    areaFictitiousConsumption[0] += load.getP0() * (1 + lossFactor);
                }
            }
        });

        if (areaFictitiousConsumption[0] != 0) {
            LOGGER.info("Area fictitious consumption : {} MW (added to the area consumption)", areaFictitiousConsumption);
        }
        this.totalConsumption = areaConsumption[0];
    }

    public double evaluateGeneration(List<Generator> startupGeneratorsAtMaxActivePower) {
        // calculate area generation (planned and available)
        // set if a group is available or not and set startedPower (?)
        final double[] pMaxAvailable = {0};
        for (GeneratorState startupGenerator : this.startupGenerators) {
            GeneratorStartup generatorStartupExtension = startupGenerator.getGenerator().getExtension(GeneratorStartup.class);
            if (generatorStartupExtension != null && generatorStartupExtension.getPlannedActivePowerSetpoint() != 0) {
                // compute the total planned generation
                if (generatorStartupExtension.getPlannedActivePowerSetpoint() > 0) {
                    this.totalPlannedActivePower = this.totalPlannedActivePower + generatorStartupExtension.getPlannedActivePowerSetpoint();
                } else {
                    // if the generator has a negative active power set point, it is added to total consumption
                    this.totalConsumption = this.totalConsumption - generatorStartupExtension.getPlannedActivePowerSetpoint();
                }
                startupGenerator.setAvailableActivePower(0); // cannot be started because already started
                startupGenerator.setActivePowerSetpoint(generatorStartupExtension.getPlannedActivePowerSetpoint());
                startupGenerator.setPlanned(true);
                startupGenerator.setAvailable(true);
                this.startedGroups.add(startupGenerator);
            } else {
                if (startupGenerator.getGenerator().getEnergySource() == EnergySource.HYDRO || generatorStartupExtension == null) {
                    // FIXME: should we ignore generators with no GeneratorStartup extension?
                    // we do not start hydro generator
                    startupGenerator.setAvailable(false);
                    continue;
                }
                double generatorPMaxAvailable = startupGenerator.evaluatePMaxAvailable(defaultReductionRatio,
                        startupGeneratorsAtMaxActivePower.contains(startupGenerator.getGenerator()));
                pMaxAvailable[0] += generatorPMaxAvailable;
            }
        }
        return pMaxAvailable[0];
    }

    public StartupArea() {
    }
}

