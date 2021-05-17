/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.hypothesis;

import com.powsybl.iidm.network.*;
import org.joda.time.DateTime;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * @author Chamseddine BENHAMED <chamseddine.benhamed at rte-france.com>
 */
public class StartupGroupComparatorTest {

    @Test
    public void allGeneratorsHavingExtension() {
        Network network = createTestNetwork();
        // extends generator
        Generator generator1 = network.getGenerator("G1");
        Generator generator2 = network.getGenerator("G2");
        Generator generator3 = network.getGenerator("G3");
        assertNotNull(generator1);
        assertNotNull(generator2);
        assertNotNull(generator3);

        generator1.newExtension(GeneratorStartupAdder.class)
                .withPredefinedActivePowerSetpoint(90f)
                .withStartUpCost(20f)
                .withMarginalCost(10f)
                .withPlannedOutageRate(0.2f)
                .withForcedOutageRate(0.8f)
                .add();
        generator2.newExtension(GeneratorStartupAdder.class)
                .withPredefinedActivePowerSetpoint(100f)
                .withStartUpCost(5f)
                .withMarginalCost(9f)
                .withPlannedOutageRate(0.7f)
                .withForcedOutageRate(0.1f)
                .add();
        generator3.newExtension(GeneratorStartupAdder.class)
                .withPredefinedActivePowerSetpoint(50f)
                .withStartUpCost(11f)
                .withMarginalCost(10f)
                .withPlannedOutageRate(0.4f)
                .withForcedOutageRate(0.3f)
                .add();

        StartupGroup startupGroup1 = new StartupGroup();
        startupGroup1.setGenerator(generator1);
        StartupGroup startupGroup2 = new StartupGroup();
        startupGroup2.setGenerator(generator2);
        StartupGroup startupGroup3 = new StartupGroup();
        startupGroup3.setGenerator(generator3);

        List<StartupGroup> startupGroups = new ArrayList<>();
        startupGroups.add(startupGroup1);
        startupGroups.add(startupGroup2);
        startupGroups.add(startupGroup3);

        startupGroups.sort(new StartupGroupComparator());

        assertEquals("G2", startupGroups.get(0).getGenerator().getNameOrId());
        assertEquals("G3", startupGroups.get(1).getGenerator().getNameOrId());
        assertEquals("G1", startupGroups.get(2).getGenerator().getNameOrId());
    }

    @Test
    public void oneGeneratorsWithoutExtension() {
        Network network = createTestNetwork();
        // extends generator
        Generator generator1 = network.getGenerator("G1");
        Generator generator2 = network.getGenerator("G2");
        Generator generator3 = network.getGenerator("G3");
        assertNotNull(generator1);
        assertNotNull(generator2);
        assertNotNull(generator3);

        generator1.newExtension(GeneratorStartupAdder.class)
                .withPredefinedActivePowerSetpoint(90f)
                .withStartUpCost(20f)
                .withMarginalCost(10f)
                .withPlannedOutageRate(0.2f)
                .withForcedOutageRate(0.8f)
                .add();

        generator3.newExtension(GeneratorStartupAdder.class)
                .withPredefinedActivePowerSetpoint(50f)
                .withStartUpCost(11f)
                .withMarginalCost(10f)
                .withPlannedOutageRate(0.4f)
                .withForcedOutageRate(0.3f)
                .add();

        StartupGroup startupGroup1 = new StartupGroup();
        startupGroup1.setGenerator(generator1);
        StartupGroup startupGroup2 = new StartupGroup();
        startupGroup2.setGenerator(generator2);
        StartupGroup startupGroup3 = new StartupGroup();
        startupGroup3.setGenerator(generator3);

        List<StartupGroup> startupGroups = new ArrayList<>();
        startupGroups.add(startupGroup1);
        startupGroups.add(startupGroup2);
        startupGroups.add(startupGroup3);

        startupGroups.sort(new StartupGroupComparator());

        assertEquals("G3", startupGroups.get(0).getGenerator().getNameOrId());
        assertEquals("G1", startupGroups.get(1).getGenerator().getNameOrId());
        assertEquals("G2", startupGroups.get(2).getGenerator().getNameOrId());
    }

    private static Network createTestNetwork() {
        Network network = NetworkFactory.findDefault().createNetwork("test", "test");
        network.setCaseDate(DateTime.parse("2016-06-27T12:27:58.535+02:00"));
        Substation s = network.newSubstation()
                .setId("S")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vl = s.newVoltageLevel()
                .setId("VL")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        vl.getBusBreakerView().newBus()
                .setId("B")
                .add();

        vl.newGenerator()
                .setId("G1")
                .setBus("B")
                .setConnectableBus("B")
                .setTargetP(100)
                .setTargetV(380)
                .setVoltageRegulatorOn(true)
                .setMaxP(100)
                .setMinP(0)
                .add();
        vl.newGenerator()
                .setId("G2")
                .setBus("B")
                .setConnectableBus("B")
                .setTargetP(100)
                .setTargetV(380)
                .setVoltageRegulatorOn(true)
                .setMaxP(100)
                .setMinP(0)
                .add();
        vl.newGenerator()
                .setId("G3")
                .setBus("B")
                .setConnectableBus("B")
                .setTargetP(100)
                .setTargetV(380)
                .setVoltageRegulatorOn(true)
                .setMaxP(100)
                .setMinP(0)
                .add();

        return network;
    }
}
