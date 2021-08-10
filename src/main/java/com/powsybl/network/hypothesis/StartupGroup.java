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
public class StartupGroup {
    private boolean usable = false;
    private double availablePower = 0;
    private double setPointPower = 0;
    private boolean imposed = false;
    private Generator generator;

    public StartupGroup(boolean usable, double availablePower, double setPointPower, boolean imposed, Generator generator) {
        this.usable = usable;
        this.availablePower = availablePower;
        this.setPointPower = setPointPower;
        this.imposed = imposed;
        this.generator = generator;
    }

    public StartupGroup() {
    }

    public boolean isUsable() {
        return usable;
    }

    public void setUsable(boolean usable) {
        this.usable = usable;
    }

    public double getAvailablePower() {
        return availablePower;
    }

    public void setAvailablePower(double availablePower) {
        this.availablePower = availablePower;
    }

    public double getSetPointPower() {
        return setPointPower;
    }

    public void setSetPointPower(double setPointPower) {
        this.setPointPower = setPointPower;
    }

    public boolean isImposed() {
        return imposed;
    }

    public void setImposed(boolean imposed) {
        this.imposed = imposed;
    }

    public Generator getGenerator() {
        return generator;
    }

    public void setGenerator(Generator generator) {
        this.generator = generator;
    }
}

