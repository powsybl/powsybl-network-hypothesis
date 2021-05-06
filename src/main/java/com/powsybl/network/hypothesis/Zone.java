/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.hypothesis;

import com.powsybl.iidm.network.Country;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * @author Chamseddine BENHAMED <chamseddine.benhamed at rte-france.com>
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Zone {
    private int num;
    private StartupType startupType = StartupType.EMPIL_ECO;
    private boolean canStart = true;
    private List<Country> countries;
    private String name;
    private List<StartupGroup> initialGroups;
    private List<StartupGroup> startedGroups;
    private double imposedPower = 0.0;
}

