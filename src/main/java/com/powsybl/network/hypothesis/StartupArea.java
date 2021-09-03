/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.hypothesis;

import com.powsybl.iidm.network.Country;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Chamseddine BENHAMED <chamseddine.benhamed at rte-france.com>
 */
public class StartupArea {
    private String name;
    private int num;
    private StartupType startupType = StartupType.PRECEDENCE_ECONOMIC;
    private boolean active = true;
    private List<Country> countries = new ArrayList<>(); // This attribute is not used for the first version
    private List<StartupGenerator> startupGenerators = new ArrayList<>(); // groupsToBeStarted?
    private List<StartupGenerator> startedGroups = new ArrayList<>();
    private double totalPlannedActivePower = 0;
    private double totalConsumption = 0;

    public StartupArea() {

    }

    public static StartupAreaBuilder builder() {
        return new StartupAreaBuilder();
    }

    public StartupArea(String name, int num, StartupType startupType, boolean active, List<Country> countries,
                       List<StartupGenerator> startupGenerators, List<StartupGenerator> startedGroups, double totalPlannedActivePower, double totalConsumption) {
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

    public List<StartupGenerator> getStartupGenerators() {
        return startupGenerators;
    }

    public void setStartupGenerators(List<StartupGenerator> startupGenerators) {
        this.startupGenerators = startupGenerators;
    }

    public List<StartupGenerator> getStartedGroups() {
        return startedGroups;
    }

    public void setStartedGroups(List<StartupGenerator> startedGroups) {
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
}

