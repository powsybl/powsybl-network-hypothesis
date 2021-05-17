/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.hypothesis;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Chamseddine BENHAMED <chamseddine.benhamed at rte-france.com>
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
// used for Mexico startup
public class StartupRegion {
    String name;
    int num;
    double consumption = 0;
    double startedPower = 0;
    List<StartupGroup> marginalGroups = new ArrayList<>();
    double availablePower = 0;
    double adjustmentToBeRealized = 0;

    public double getRegionBalance() {
        return startedPower - consumption;
    }
}
