/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.hypothesis;

import java.util.Comparator;

/**
 * @author Chamseddine BENHAMED <chamseddine.benhamed at rte-france.com>
 */
public class StartupGeneratorComparator implements Comparator<StartupGenerator> {
    @Override
    public int compare(StartupGenerator startupGenerator1, StartupGenerator startupGenerator2) {

        double cost1 = startupGenerator1.getGenerator().getExtension(GeneratorStartup.class) != null ? startupGenerator1.getGenerator().getExtension(GeneratorStartup.class).getStartupCost() : Double.MAX_VALUE;
        double cost2 = startupGenerator2.getGenerator().getExtension(GeneratorStartup.class) != null ? startupGenerator2.getGenerator().getExtension(GeneratorStartup.class).getStartupCost() : Double.MAX_VALUE;

        if (cost1 == cost2) {
            return 0;
        }

        return cost1 < cost2 ? -1 : 1;
    }
}

