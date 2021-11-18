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
import com.powsybl.iidm.network.Load;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.iidm.network.test.FourSubstationsNodeBreakerFactory;
import com.powsybl.iidm.network.test.HvdcTestNetwork;
import com.powsybl.iidm.xml.NetworkXml;
import org.joda.time.DateTime;
import org.junit.Test;

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
        HvdcHypothesis.convertHvdcToLoads(hvdcLine);
        roundTripXmlTest(network, NetworkXml::writeAndValidate, NetworkXml::validateAndRead, "/foursubstation-loads-hvdc.xml");
    }

    @Test
    public void testGeneratorsFromHvdc() throws  IOException {
        Network network = HvdcTestNetwork.createVsc();
        network.setCaseDate(DateTime.parse("2021-11-12T10:53:49.274+01:00"));
        HvdcLine hvdcLine = network.getHvdcLine("L");
        HvdcHypothesis.convertHvdcToGenerators(hvdcLine);
        roundTripXmlTest(network, NetworkXml::writeAndValidate, NetworkXml::validateAndRead, "/hvdcnetwrok-generators-hvdc.xml");
    }
}
