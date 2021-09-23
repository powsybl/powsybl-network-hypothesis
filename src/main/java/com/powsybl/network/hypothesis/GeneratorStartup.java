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
        return "startup";
    }

    double getPredefinedActivePowerSetpoint();

    GeneratorStartup setPredefinedActivePowerSetpoint(double predefinedActivePowerSetpoint);

    double getStartUpCost();

    GeneratorStartup setStartUpCost(double startUpCost);

    double getMarginalCost();

    GeneratorStartup setMarginalCost(double marginalCost);

    double getPlannedOutageRate();

    GeneratorStartup setPlannedOutageRate(double plannedOutageRate);

    double getForcedOutageRate();

    GeneratorStartup setForcedOutageRate(double forcedOutageRate);
}
