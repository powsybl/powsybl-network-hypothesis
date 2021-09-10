/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.hypothesis;

import com.powsybl.commons.AbstractConverterTest;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.TopologyKind;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.iidm.network.test.FictitiousSwitchFactory;
import com.powsybl.iidm.xml.NetworkXml;
import org.joda.time.DateTime;
import org.junit.Test;

import java.io.IOException;

/**
 * @author Miora Ralambotiana <miora.ralambotiana at rte-france.com>
 */
public class HypothesisUtilsTest extends AbstractConverterTest {

    @Test
    public void testBb() throws IOException {
        Network network = EurostagTutorialExample1Factory.create();
        network.setCaseDate(DateTime.parse("2021-08-27T14:44:56.567+02:00"));
        HypothesisUtils.createVoltageLevelOnLine(network.getLine("NHV1_NHV2_1"), TopologyKind.BUS_BREAKER);
        roundTripXmlTest(network, NetworkXml::writeAndValidate, NetworkXml::validateAndRead, "/eurostag-line-split.xml");
    }

    @Test
    public void testNb() throws IOException {
        Network network = FictitiousSwitchFactory.create();
        network.setCaseDate(DateTime.parse("2021-08-27T14:44:56.567+02:00"));
        HypothesisUtils.createVoltageLevelOnLine(network.getLine("CJ"));
        roundTripXmlTest(network, NetworkXml::writeAndValidate, NetworkXml::validateAndRead, "/fictitious-switch-line-split.xml");
    }

    @Test
    public void testMixed() throws IOException {
        Network network = EurostagTutorialExample1Factory.create();
        network.setCaseDate(DateTime.parse("2021-08-27T14:44:56.567+02:00"));
        HypothesisUtils.createVoltageLevelOnLine(network.getLine("NHV1_NHV2_1"), TopologyKind.NODE_BREAKER);
        roundTripXmlTest(network, NetworkXml::writeAndValidate, NetworkXml::validateAndRead, "/eurostag-line-split-nb.xml");
    }

    @Test
    public void testBbWithBreakers() throws IOException {
        Network network = EurostagTutorialExample1Factory.create();
        network.setCaseDate(DateTime.parse("2021-08-27T14:44:56.567+02:00"));
        HypothesisUtils.createVoltageLevelOnLine(network.getLine("NHV1_NHV2_1"), TopologyKind.BUS_BREAKER, true);
        roundTripXmlTest(network, NetworkXml::writeAndValidate, NetworkXml::validateAndRead, "/eurostag-line-split-with-breakers.xml");
    }

    @Test
    public void testNbWithBreakers() throws IOException {
        Network network = FictitiousSwitchFactory.create();
        network.setCaseDate(DateTime.parse("2021-08-27T14:44:56.567+02:00"));
        HypothesisUtils.createVoltageLevelOnLine(network.getLine("CJ"), true);
        roundTripXmlTest(network, NetworkXml::writeAndValidate, NetworkXml::validateAndRead, "/fictitious-switch-line-split-with-breakers.xml");
    }
}
