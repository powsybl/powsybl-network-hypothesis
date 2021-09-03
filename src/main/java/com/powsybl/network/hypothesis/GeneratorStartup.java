/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.hypothesis;

import com.powsybl.commons.extensions.Extension;
import com.powsybl.iidm.network.Generator;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public interface GeneratorStartup extends Extension<Generator> {

    @Override
    default String getName() {
        return "generatorStartup";
    }

    /**
     * The active power target planned by the market (in MW).
     */
    double getPlannedActivePowerSetpoint();

    GeneratorStartup setPlannedActivePowerSetpoint(double plannedActivePowerSetpoint);

    /**
     * How does it cost to start this generator.
     */
    double getStartupCost();

    GeneratorStartup setStartupCost(double startupCost);

    /**
     * How does it cost to increase the production of one unit (in general one MW).
     */
    double getMarginalCost();

    GeneratorStartup setMarginalCost(double marginalCost);

    double getPlannedOutageRate();

    /**
     * Rate of planned unavailability (no unit).
     */
    GeneratorStartup setPlannedOutageRate(double plannedOutageRate);

    /**
     * Rate of force unavailability (not forecast, no unit)
     */
    double getForcedOutageRate();

    GeneratorStartup setForcedOutageRate(double forcedOutageRate);
}
