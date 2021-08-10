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
public class StartupZoneBuilder {
    private String name;
    private int num;
    private StartupType startupType = StartupType.EMPIL_ECO;
    private boolean canStart = true;
    // This attribute is not used for the first version
    private List<Country> countries = new ArrayList<>();
    private List<StartupGroup> startupGroups = new ArrayList<>();
    private List<StartupGroup> startedGroups = new ArrayList<>();
    private double imposedPower = 0;
    private double consumption = 0;

    public StartupZoneBuilder() {
    }

    public StartupZoneBuilder name(String name) {
        this.name = name;
        return this;
    }

    public StartupZoneBuilder num(int num) {
        this.num = num;
        return this;
    }

    public StartupZoneBuilder startupType(StartupType startupType) {
        this.startupType = startupType;
        return this;
    }

    public StartupZoneBuilder canStart(boolean canStart) {
        this.canStart = canStart;
        return this;
    }

    public StartupZoneBuilder countries(List<Country> countries) {
        this.countries = countries;
        return this;
    }

    public StartupZoneBuilder startupGroups(List<StartupGroup> startupGroups) {
        this.startupGroups = startupGroups;
        return this;
    }

    public StartupZoneBuilder startedGroups(List<StartupGroup> startedGroups) {
        this.startedGroups = startedGroups;
        return this;
    }

    public StartupZoneBuilder imposedPower(int imposedPower) {
        this.imposedPower = imposedPower;
        return this;
    }

    public StartupZone build() {
        return new StartupZone(name, num, startupType, canStart, countries,
                startupGroups, startedGroups, imposedPower, consumption);
    }
}

