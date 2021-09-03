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
public class StartupAreaBuilder {
    private String name;
    private int num;
    private StartupType startupType = StartupType.PRECEDENCE_ECONOMIC;
    private boolean active = true; // meaning not clear.
    private List<Country> countries = new ArrayList<>(); // This attribute is not used for the first version
    private List<StartupGenerator> startupGenerators = new ArrayList<>(); // groupsToBeStarted?
    private List<StartupGenerator> startedGroups = new ArrayList<>();
    private double totalPlannedActivePower = 0; // FIXME?
    private double totalConsumption = 0;

    public StartupAreaBuilder name(String name) {
        this.name = name;
        return this;
    }

    public StartupAreaBuilder num(int num) {
        this.num = num;
        return this;
    }

    public StartupAreaBuilder startupType(StartupType startupType) {
        this.startupType = startupType;
        return this;
    }

    public StartupAreaBuilder isActive(boolean active) {
        this.active = active;
        return this;
    }

    public StartupAreaBuilder countries(List<Country> countries) {
        this.countries = countries;
        return this;
    }

    public StartupAreaBuilder startupGroups(List<StartupGenerator> startupGenerators) {
        this.startupGenerators = startupGenerators;
        return this;
    }

    public StartupAreaBuilder startedGroups(List<StartupGenerator> startedGroups) {
        this.startedGroups = startedGroups;
        return this;
    }

    public StartupAreaBuilder plannedActivePower(int plannedActivePower) {
        this.totalPlannedActivePower = plannedActivePower;
        return this;
    }

    public StartupArea build() {
        return new StartupArea(name, num, startupType, active, countries,
                startupGenerators, startedGroups, totalPlannedActivePower, totalConsumption);
    }
}

