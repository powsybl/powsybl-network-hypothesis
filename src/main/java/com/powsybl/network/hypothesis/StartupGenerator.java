/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.hypothesis;

import com.powsybl.iidm.network.Generator;

/**
 * @author Chamseddine BENHAMED <chamseddine.benhamed at rte-france.com>
 */
public class StartupGenerator {
    private boolean available = false;
    private double availableActivePower = 0;
    private double activePowerSetpoint = 0;
    private boolean planned = false;
    private Generator generator;

    public StartupGenerator(boolean available, double availableActivePower, double activePowerSetpoint, boolean planned, Generator generator) {
        this.available = available;
        this.availableActivePower = availableActivePower;
        this.activePowerSetpoint = activePowerSetpoint;
        this.planned = planned;
        this.generator = generator;
    }

    public StartupGenerator() {
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public double getAvailableActivePower() {
        return availableActivePower;
    }

    public void setAvailableActivePower(double availableActivePower) {
        this.availableActivePower = availableActivePower;
    }

    public double getActivePowerSetpoint() {
        return activePowerSetpoint;
    }

    public void setActivePowerSetpoint(double activePowerSetpoint) {
        this.activePowerSetpoint = activePowerSetpoint;
    }

    public boolean isPlanned() {
        return planned;
    }

    public void setPlanned(boolean planned) {
        this.planned = planned;
    }

    public Generator getGenerator() {
        return generator;
    }

    public void setGenerator(Generator generator) {
        this.generator = generator;
    }
}

