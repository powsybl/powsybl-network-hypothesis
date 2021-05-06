/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.hypothesis;

import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;

/**
 * @author Chamseddine BENHAMED <chamseddine.benhamed at rte-france.com>
 */
public class GeneratorsStartupAlgorithmTest {

    @Test
    public void test() {
        //Network network = createTestNetwork();
        Network network = EurostagTutorialExample1Factory.create();

        // extends generator
        Generator generator = network.getGenerator("GEN");
        assertNotNull(generator);
        generator.newExtension(GeneratorStartupAdder.class)
                .withPredefinedActivePowerSetpoint(90f)
                .withStartUpCost(5f)
                .withMarginalCost(10f)
                .withPlannedOutageRate(0.8f)
                .withForcedOutageRate(0.7f)
                .add();
        GeneratorStartup startup = generator.getExtension(GeneratorStartup.class);
        generator.addExtension(GeneratorStartup.class, startup);

        GeneratorsStartupAlgorithm generatorsStartupAlgorithm = new GeneratorsStartupAlgorithm();
        generatorsStartupAlgorithm.apply(network, StartupMarginalGroupType.CLASSIC, 0.1, 0.02, 0, 0);
    }
}
