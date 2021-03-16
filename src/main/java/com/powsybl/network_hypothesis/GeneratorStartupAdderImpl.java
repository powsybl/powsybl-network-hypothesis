/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network_hypothesis;

import com.powsybl.commons.extensions.AbstractExtensionAdder;
import com.powsybl.iidm.network.Generator;

/**
 * @author Jérémy Labous <jlabous at silicom.fr>
 */
public class GeneratorStartupAdderImpl extends AbstractExtensionAdder<Generator, GeneratorStartup> implements GeneratorStartupAdder {

    private float predefinedActivePowerSetpoint;

    private float marginalCost;

    private float plannedOutageRate;

    private float forcedOutageRate;

    public GeneratorStartupAdderImpl(Generator generator) {
        super(generator);
    }

    @Override
    protected GeneratorStartup createExtension(Generator extendable) {
        return new GeneratorStartupImpl(extendable, predefinedActivePowerSetpoint, marginalCost, plannedOutageRate, forcedOutageRate);
    }

    @Override
    public GeneratorStartupAdderImpl withPredefinedActivePowerSetpoint(float predefinedActivePowerSetpoint) {
        this.predefinedActivePowerSetpoint = predefinedActivePowerSetpoint;
        return this;
    }

    @Override
    public GeneratorStartupAdderImpl withMarginalCost(float marginalCost) {
        this.marginalCost = marginalCost;
        return this;
    }

    @Override
    public GeneratorStartupAdderImpl withPlannedOutageRate(float plannedOutageRate) {
        this.plannedOutageRate = plannedOutageRate;
        return this;
    }

    @Override
    public GeneratorStartupAdderImpl withForcedOutageRate(float forcedOutageRate) {
        this.forcedOutageRate = forcedOutageRate;
        return this;
    }
}
