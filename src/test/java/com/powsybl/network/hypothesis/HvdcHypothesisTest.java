/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.hypothesis;

import com.powsybl.commons.AbstractConverterTest;
import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.HvdcLine;
import com.powsybl.iidm.network.LccConverterStation;
import com.powsybl.iidm.network.Load;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VscConverterStation;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.iidm.network.test.FourSubstationsNodeBreakerFactory;
import com.powsybl.iidm.network.test.HvdcTestNetwork;
import com.powsybl.iidm.xml.NetworkXml;
import org.joda.time.DateTime;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

/**
 * @author Marcos de Miguel <demiguelm at aia.es>
 */
public class HvdcHypothesisTest extends AbstractConverterTest {

    @Test
    public void testHvdcFromGenerators() throws  IOException {
        Network network = EurostagTutorialExample1Factory.createWithMultipleConnectedComponents();
        network.setCaseDate(DateTime.parse("2021-11-12T10:53:49.274+01:00"));
        Generator gen1 = network.getGenerator("GEN");
        Generator gen2 = network.getGenerator("GEN2");
        gen2.setTargetP(-gen2.getTargetP());
        HvdcHypothesis.convertGeneratorsToHvdc(gen1, gen2);
        roundTripXmlTest(network, NetworkXml::writeAndValidate, NetworkXml::validateAndRead, "/eurostag-hvdc-generators.xml");
    }

    @Test
    public void testHvdcFromLoads() throws  IOException {
        Network network = EurostagTutorialExample1Factory.createWithMultipleConnectedComponents();
        network.setCaseDate(DateTime.parse("2021-11-12T10:53:49.274+01:00"));
        Load load1 = network.getLoad("LOAD");
        Load load2 = network.getLoad("LOAD2");
        load2.setP0(-load2.getP0());
        HvdcHypothesis.convertLoadsToHvdc(load1, load2);
        roundTripXmlTest(network, NetworkXml::writeAndValidate, NetworkXml::validateAndRead, "/eurostag-hvdc-loads.xml");
    }

    @Test
    public void testReactiveLimitsCurve() throws  IOException {
        Network network = FourSubstationsNodeBreakerFactory.create();
        network.setCaseDate(DateTime.parse("2021-11-12T10:53:49.274+01:00"));
        Generator gen1 = network.getGenerator("GH1");
        Generator gen2 = network.getGenerator("GH3");
        gen1.setTargetP(-gen1.getTargetP());
        HvdcHypothesis.convertGeneratorsToHvdc(gen1, gen2);
        roundTripXmlTest(network, NetworkXml::writeAndValidate, NetworkXml::validateAndRead, "/foursubstation-curve.xml");
    }

    @Test
    public void testLoadsFromHvdc() throws  IOException {
        Network network = FourSubstationsNodeBreakerFactory.create();
        network.setCaseDate(DateTime.parse("2021-11-12T10:53:49.274+01:00"));
        HvdcLine hvdcLine = network.getHvdcLine("HVDC2");
        HvdcHypothesis.convertHvdcToInjection(hvdcLine);
        roundTripXmlTest(network, NetworkXml::writeAndValidate, NetworkXml::validateAndRead, "/foursubstation-loads-hvdc.xml");
    }

    @Test
    public void testGeneratorsFromHvdc() throws  IOException {
        Network network = HvdcTestNetwork.createVsc();
        network.setCaseDate(DateTime.parse("2021-11-12T10:53:49.274+01:00"));
        HvdcLine hvdcLine = network.getHvdcLine("L");
        HvdcHypothesis.convertHvdcToInjection(hvdcLine);
        roundTripXmlTest(network, NetworkXml::writeAndValidate, NetworkXml::validateAndRead, "/hvdcnetwork-generators-hvdc.xml");
    }

    @Test
    public void testHvdcLccToLoadsToHvdcLcc() throws IOException {
        Network network = HvdcTestNetwork.createLcc();
        network.setCaseDate(DateTime.parse("2021-11-12T10:53:49.274+01:00"));
        HvdcLine hvdcLine = network.getHvdcLine("L");

        adjustHvdcLine(hvdcLine, 686.0, 829.0);
        // Converter2 is rectifier and converter1 is inverter
        adjustLccConverter((LccConverterStation) hvdcLine.getConverterStation2(), 0.64, getPowerFactor(696.0, 94.0), 696.0, 94.0);
        adjustLccConverter((LccConverterStation) hvdcLine.getConverterStation1(), 0.63, getPowerFactor(-682.0, -49.0), -682.0, -49.0);

        HvdcHypothesis.convertHvdcToInjection(hvdcLine);
        Load load1 = network.getLoad("C1-Load");
        Load load2 = network.getLoad("C2-Load");

        HvdcHypothesis.convertLoadsToHvdc(load1, load2);
        HvdcLine hvdcLine1 = network.getHvdcLine("C1-Load_C2-Load_HVDC");

        double tol = 1.0e-6;
        assertEquals(686.048440, hvdcLine1.getActivePowerSetpoint(), tol);
        assertEquals(823.258128, hvdcLine1.getMaxP(), tol);

        LccConverterStation converterStation11 = (LccConverterStation) hvdcLine1.getConverterStation1();
        assertEquals(0.637016, converterStation11.getLossFactor(), tol);
        assertEquals(-0.997429, converterStation11.getPowerFactor(), tol);
        assertEquals(-682.0, converterStation11.getTerminal().getP(), tol);
        assertEquals(-49.0, converterStation11.getTerminal().getQ(), tol);

        LccConverterStation converterStation12 = (LccConverterStation) hvdcLine1.getConverterStation2();
        assertEquals(0.632984, converterStation12.getLossFactor(), tol);
        assertEquals(0.991003, converterStation12.getPowerFactor(), tol);
        assertEquals(696.0, converterStation12.getTerminal().getP(), tol);
        assertEquals(94.0, converterStation12.getTerminal().getQ(), tol);
    }

    @Test
    public void testHvdcVscToLoadsToHvdcVsc() throws IOException {
        Network network = HvdcTestNetwork.createVsc();
        network.setCaseDate(DateTime.parse("2021-11-12T10:53:49.274+01:00"));
        HvdcLine hvdcLine = network.getHvdcLine("L");

        adjustHvdcLine(hvdcLine, 686.0, 829.0);
        // Converter2 is rectifier and converter1 is inverter
        adjustVscConverter((VscConverterStation) hvdcLine.getConverterStation2(), 0.64, getPowerFactor(696.0, 94.0), 405.0, 90.0, 696.0, 94.0);
        adjustVscConverter((VscConverterStation) hvdcLine.getConverterStation1(), 0.63, getPowerFactor(-682.0, -49.0), 401.0, -50.0, -682.0, -49.0);

        HvdcHypothesis.convertHvdcToInjection(hvdcLine);
        Generator generator1 = network.getGenerator("C1-Generator");
        Generator generator2 = network.getGenerator("C2-Generator");

        HvdcHypothesis.convertGeneratorsToHvdc(generator1, generator2);
        HvdcLine hvdcLine1 = network.getHvdcLine("C1-Generator_C2-Generator_HVDC");

        double tol = 1.0e-6;
        assertEquals(686.048440, hvdcLine1.getActivePowerSetpoint(), tol);
        assertEquals(829.0, hvdcLine1.getMaxP(), tol);

        VscConverterStation converterStation11 = (VscConverterStation) hvdcLine1.getConverterStation1();
        assertEquals(0.637016, converterStation11.getLossFactor(), tol);
        assertEquals(-682.0, converterStation11.getTerminal().getP(), tol);
        assertEquals(-49.0, converterStation11.getTerminal().getQ(), tol);

        VscConverterStation converterStation1 = (VscConverterStation) hvdcLine.getConverterStation1();
        assertEquals(converterStation1.getVoltageSetpoint(), converterStation11.getVoltageSetpoint(), tol);
        assertEquals(converterStation1.getReactivePowerSetpoint(), converterStation11.getReactivePowerSetpoint(), tol);
        assertEquals(converterStation1.getRegulatingTerminal(), converterStation11.getRegulatingTerminal());
        assertEquals(converterStation1.isVoltageRegulatorOn(), converterStation11.isVoltageRegulatorOn());

        VscConverterStation converterStation12 = (VscConverterStation) hvdcLine1.getConverterStation2();
        assertEquals(0.632984, converterStation12.getLossFactor(), tol);
        assertEquals(696.0, converterStation12.getTerminal().getP(), tol);
        assertEquals(94.0, converterStation12.getTerminal().getQ(), tol);

        VscConverterStation converterStation2 = (VscConverterStation) hvdcLine.getConverterStation2();
        assertEquals(converterStation2.getVoltageSetpoint(), converterStation12.getVoltageSetpoint(), tol);
        assertEquals(converterStation2.getReactivePowerSetpoint(), converterStation12.getReactivePowerSetpoint(), tol);
        assertEquals(converterStation2.getRegulatingTerminal(), converterStation12.getRegulatingTerminal());
        assertEquals(converterStation2.isVoltageRegulatorOn(), converterStation12.isVoltageRegulatorOn());
    }

    private static void adjustLccConverter(LccConverterStation converter, double lossFactor,
        double powerFactor, double activePower, double reactivePower) {
        converter.setLossFactor((float) lossFactor);
        converter.setPowerFactor((float) powerFactor);
        converter.getTerminal().setP(activePower);
        converter.getTerminal().setQ(reactivePower);
    }

    private static void adjustVscConverter(VscConverterStation converter, double lossFactor,
        double powerFactor, double voltageSetpoint, double reactivePowerSetpoint, double activePower, double reactivePower) {
        converter.setLossFactor((float) lossFactor);
        converter.setVoltageSetpoint(voltageSetpoint);
        converter.setReactivePowerSetpoint(reactivePowerSetpoint);
        converter.setRegulatingTerminal(converter.getTerminal());
        converter.setVoltageRegulatorOn(true);
        converter.getTerminal().setP(activePower);
        converter.getTerminal().setQ(reactivePower);
    }

    private static void adjustHvdcLine(HvdcLine hvdcLine, double activePowerSetpoint, double maxP) {
        hvdcLine.setActivePowerSetpoint(activePowerSetpoint);
        hvdcLine.setMaxP(maxP);
    }

    private static double getPowerFactor(double activePower, double reactivePower) {
        return activePower / Math.hypot(activePower, reactivePower);
    }
}
