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

import static org.junit.Assert.assertEquals;

/**
 * @author Chamseddine BENHAMED <chamseddine.benhamed at rte-france.com>
 */
public class GeneratorsStartupAlgorithmTest {

    @Test
    public void testGeneratorsStartupAlgorithmWithClassicalAlgorithm() {
        Network network = createTestNetwork();
        assertEquals(4, network.getGeneratorCount());
        assertEquals(3, network.getLoadCount());
        GeneratorsStartupAlgorithm generatorsStartupAlgorithm = new GeneratorsStartupAlgorithm();
        generatorsStartupAlgorithm.apply(network, StartupMarginalGroupType.CLASSIC, 0.1, 0.02, 0, 0, new ArrayList<>());

        assertEquals(76.95, network.getGenerator("G1").getTargetP(), 0.001);
        assertEquals(76.95, network.getGenerator("G2").getTargetP(), 0.001);
        assertEquals(29.5, network.getGenerator("G3").getTargetP(), 0.001);
        assertEquals(-10, network.getGenerator("G4").getTargetP(), 0.001);
    }

    @Test
    public void testGeneratorsStartupAlgorithmWithClassicalAlgorithmAndPMaxGenerators() {
        Network network = createTestNetwork();

        List<Generator> startupGroupsPowerMax = new ArrayList<>();
        startupGroupsPowerMax.add(network.getGenerator("G3"));

        GeneratorsStartupAlgorithm generatorsStartupAlgorithm = new GeneratorsStartupAlgorithm();
        generatorsStartupAlgorithm.apply(network, StartupMarginalGroupType.CLASSIC, 0.1, 0.02, 0, 0, startupGroupsPowerMax);

        assertEquals(76.95, network.getGenerator("G1").getTargetP(), 0.001);
        assertEquals(76.95, network.getGenerator("G2").getTargetP(), 0.001);
        assertEquals(190, network.getGenerator("G3").getTargetP(), 0.001);
        assertEquals(-10, network.getGenerator("G4").getTargetP(), 0.001);
    }

    @Test
    public void testGeneratorsStartupAlgorithmWithConsumptionGreaterThanProduction() {
        Network network = createTestNetworkWithLoadsValue(200, 300, 400);
        assertEquals(4, network.getGeneratorCount());
        assertEquals(3, network.getLoadCount());
        GeneratorsStartupAlgorithm generatorsStartupAlgorithm = new GeneratorsStartupAlgorithm();
        generatorsStartupAlgorithm.apply(network, StartupMarginalGroupType.CLASSIC, 0.1, 0.02, 0, 0, new ArrayList<>());

        // No generator is started
        assertEquals(0, network.getGenerator("G1").getTargetP(), 0.001);
        assertEquals(0, network.getGenerator("G2").getTargetP(), 0.001);
        assertEquals(0, network.getGenerator("G3").getTargetP(), 0.001);
        assertEquals(0, network.getGenerator("G4").getTargetP(), 0.001);
    }

    private static Network createTestNetworkWithLoadsValue(double val1, double val2, double val3) {
        Network network = NetworkFactory.findDefault().createNetwork("test", "test");
        network.setCaseDate(DateTime.parse("2021-05-11T12:27:58.535+02:00"));
        Substation s = network.newSubstation()
                .setId("S")
                .setCountry(Country.FR)
                .add();
        network.newSubstation()
                .setId("S2")
                .setCountry(Country.FR)
                .add();

        VoltageLevel vl = s.newVoltageLevel()
                .setId("VL")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        VoltageLevel vl2 = s.newVoltageLevel()
                .setId("VL2")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();

        vl.getBusBreakerView().newBus()
                .setId("B")
                .add();
        vl2.getBusBreakerView().newBus()
                .setId("B2")
                .add();
        network.newLine().setVoltageLevel1("VL")
                .setId("L")
                .setVoltageLevel2("VL2")
                .setBus1("B")
                .setBus2("B2")
                .setConnectableBus1("B")
                .setConnectableBus2("B2")
                .setB1(0)
                .setB2(0)
                .setG1(0)
                .setG2(0)
                .setR(0)
                .setX(0)
                .add();

        vl.newGenerator()
                .setId("G1")
                .setName("nuclearGroup")
                .setBus("B")
                .setConnectableBus("B")
                .setEnergySource(EnergySource.NUCLEAR)
                .setTargetP(0)
                .setTargetV(380)
                .setVoltageRegulatorOn(false)
                .setMaxP(100)
                .setMinP(0)
                .setTargetQ(0)
                .add();
        vl.newGenerator()
                .setId("G2")
                .setName("thermalGroup")
                .setBus("B")
                .setConnectableBus("B")
                .setEnergySource(EnergySource.THERMAL)
                .setTargetP(0)
                .setTargetV(380)
                .setVoltageRegulatorOn(false)
                .setMaxP(100)
                .setMinP(0)
                .setTargetQ(0)
                .add();
        vl2.newGenerator()
                .setId("G3")
                .setName("thermalGroup")
                .setBus("B2")
                .setConnectableBus("B2")
                .setEnergySource(EnergySource.THERMAL)
                .setTargetP(0)
                .setTargetV(380)
                .setVoltageRegulatorOn(false)
                .setMaxP(200)
                .setMinP(0)
                .setTargetQ(0)
                .add();

        vl2.newGenerator()
                .setId("G4")
                .setName("hydroGroup")
                .setBus("B2")
                .setConnectableBus("B2")
                .setEnergySource(EnergySource.HYDRO)
                .setTargetP(0)
                .setTargetV(380)
                .setVoltageRegulatorOn(false)
                .setMaxP(20)
                .setMinP(-10)
                .setTargetQ(0)
                .add();

        vl.newLoad()
                .setId("L1")
                .setName("Load1")
                .setBus("B")
                .setConnectableBus("B")
                .setP0(val1)
                .setQ0(0)
                .add();

        vl2.newLoad()
                .setId("L2")
                .setName("Load2")
                .setBus("B2")
                .setConnectableBus("B2")
                .setP0(val2)
                .setQ0(0)
                .add();

        vl2.newLoad()
                .setId("L3")
                .setName("Load3")
                .setBus("B2")
                .setConnectableBus("B2")
                .setP0(val3)
                .setQ0(0)
                .add();

        Generator generator1 = network.getGenerator("G1");
        generator1.newExtension(GeneratorStartupAdder.class)
                .withPredefinedActivePowerSetpoint(Double.MAX_VALUE)
                .withStartUpCost(2)
                .withMarginalCost(2)
                .withPlannedOutageRate(0.1)
                .withForcedOutageRate(0.1)
                .add();

        Generator generator2 = network.getGenerator("G2");
        generator2.newExtension(GeneratorStartupAdder.class)
                .withPredefinedActivePowerSetpoint(Double.MAX_VALUE)
                .withStartUpCost(2.2)
                .withMarginalCost(2.2)
                .withPlannedOutageRate(0.1)
                .withForcedOutageRate(0.1)
                .add();

        Generator generator3 = network.getGenerator("G3");
        generator3.newExtension(GeneratorStartupAdder.class)
                .withPredefinedActivePowerSetpoint(Double.MAX_VALUE)
                .withStartUpCost(3)
                .withMarginalCost(3)
                .withPlannedOutageRate(0.1)
                .withForcedOutageRate(0.1)
                .add();

        Generator generator4 = network.getGenerator("G4");
        generator4.newExtension(GeneratorStartupAdder.class)
                .withPredefinedActivePowerSetpoint(-10)
                .withStartUpCost(1)
                .withMarginalCost(1)
                .withPlannedOutageRate(0.1)
                .withForcedOutageRate(0.1)
                .add();

        return network;
    }

    private static Network createTestNetwork() {
        return createTestNetworkWithLoadsValue(50, 60, 60);
    }
}
