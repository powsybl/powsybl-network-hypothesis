/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.hypothesis;

import com.powsybl.iidm.network.Country;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Chamseddine BENHAMED <chamseddine.benhamed at rte-france.com>
 */
public class StartupZone {
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

    public StartupZone() {

    }

    public static StartupZoneBuilder builder() {
        return new StartupZoneBuilder();
    }

    public StartupZone(String name, int num, StartupType startupType, boolean canStart, List<Country> countries,
                       List<StartupGroup> startupGroups, List<StartupGroup> startedGroups, double imposedPower, double consumption) {
        this.name = name;
        this.num = num;
        this.startupType = startupType;
        this.canStart = canStart;
        this.countries = countries;
        this.startupGroups = startupGroups;
        this.startedGroups = startedGroups;
        this.imposedPower = imposedPower;
        this.consumption = consumption;
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

    public boolean isCanStart() {
        return canStart;
    }

    public void setCanStart(boolean canStart) {
        this.canStart = canStart;
    }

    public List<Country> getCountries() {
        return countries;
    }

    public void setCountries(List<Country> countries) {
        this.countries = countries;
    }

    public List<StartupGroup> getStartupGroups() {
        return startupGroups;
    }

    public void setStartupGroups(List<StartupGroup> startupGroups) {
        this.startupGroups = startupGroups;
    }

    public List<StartupGroup> getStartedGroups() {
        return startedGroups;
    }

    public void setStartedGroups(List<StartupGroup> startedGroups) {
        this.startedGroups = startedGroups;
    }

    public double getImposedPower() {
        return imposedPower;
    }

    public void setImposedPower(double imposedPower) {
        this.imposedPower = imposedPower;
    }

    public double getConsumption() {
        return consumption;
    }

    public void setConsumption(double consumption) {
        this.consumption = consumption;
    }
}

