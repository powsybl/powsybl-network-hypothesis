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
public class GeneratorState {
    private boolean available = false;
    private double availableActivePower = 0;
    private double activePowerSetpoint = 0;
    private boolean planned = false;
    private Generator generator;
    // parameters
    private double nuclearAdequacyMarginRatio = 0.05; // the ratio of generation reserved for adequacy for nuclear units
    private double thermalAdequacyMarginRatio = 0.05; // the ratio of generation reserved for adequacy for thermal units
    private double hydroAdequacyMarginRatio = 0.1; // the ratio of generation reserved for adequacy for hydro units

    public GeneratorState(boolean available, double availableActivePower, double activePowerSetpoint, boolean planned, Generator generator) {
        this.available = available;
        this.availableActivePower = availableActivePower;
        this.activePowerSetpoint = activePowerSetpoint;
        this.planned = planned;
        this.generator = generator;
    }

    public GeneratorState() {
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

    public double evaluatePMaxAvailable(double defaultReductionRatio, boolean isMaxP) {
        double pMaxAvailable;
        if (isMaxP) {
            pMaxAvailable = generator.getMaxP();
        } else {
            GeneratorStartup generatorStartup = generator.getExtension(GeneratorStartup.class);
            pMaxAvailable = generator.getMaxP();
            double plannedOutageRate = generatorStartup != null ? generatorStartup.getPlannedOutageRate() : 0;
            double forcedOutageRate = generatorStartup != null ? generatorStartup.getForcedOutageRate() : 0;
            if (plannedOutageRate != 0 || forcedOutageRate != 0) {
                pMaxAvailable *= (1 - forcedOutageRate) * (1 - plannedOutageRate);
            } else {
                pMaxAvailable *= 1 - defaultReductionRatio;
            }
        }
        double adequacyRatio = this.computeAdequacyMarginRatio();
        pMaxAvailable *= 1 - adequacyRatio;
        this.availableActivePower = pMaxAvailable;
        this.available = true;
        return pMaxAvailable;
    }

    private double computeAdequacyMarginRatio() {
        // ratio of active generation to be kept for frequency reserve.
        double generatorAdequacyMarginRatio = 0;

        switch (generator.getEnergySource()) {
            case HYDRO: generatorAdequacyMarginRatio = hydroAdequacyMarginRatio; break;
            case NUCLEAR: generatorAdequacyMarginRatio = nuclearAdequacyMarginRatio; break;
            case THERMAL: generatorAdequacyMarginRatio = thermalAdequacyMarginRatio; break;
            default: generatorAdequacyMarginRatio = 0;
        }
        return generatorAdequacyMarginRatio;
    }
}

