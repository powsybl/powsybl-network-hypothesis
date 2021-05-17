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
public class StartupGroupComparator implements Comparator<StartupGroup> {
    @Override
    public int compare(StartupGroup startupGroup1, StartupGroup startupGroup2) {

        double cost1 = startupGroup1.getGenerator().getExtension(GeneratorStartup.class) != null ? startupGroup1.getGenerator().getExtension(GeneratorStartup.class).getStartUpCost() : Double.MAX_VALUE;
        double cost2 = startupGroup2.getGenerator().getExtension(GeneratorStartup.class) != null ? startupGroup2.getGenerator().getExtension(GeneratorStartup.class).getStartUpCost() : Double.MAX_VALUE;

        if (cost1 == cost2) {
            return 0;
        }

        return cost1 < cost2 ? -1 : 1;
    }
}

