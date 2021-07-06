/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.hypothesis;

import com.powsybl.iidm.network.Country;
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
public class StartupZone {
    private String name;
    private int num;
    private StartupType startupType = StartupType.EMPIL_ECO;
    private boolean canStart = true;
    // This attribute is not used for the first version
    private List<Country> countries = new ArrayList<>();
    private List<StartupGroup> startupGroups = new ArrayList<>();
    private List<StartupGroup> startedGroups = new ArrayList<>();
    private double imposedPower = 0;
    private double consumption = 0;
}

