/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.hypothesis;

import com.powsybl.iidm.network.Generator;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * @author Chamseddine BENHAMED <chamseddine.benhamed at rte-france.com>
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class StartupGroup {
    private boolean usable = false;
    private double availablePower = 0;
    private double setPointPower = 0;
    private boolean imposed = false;
    private Generator generator;
}

