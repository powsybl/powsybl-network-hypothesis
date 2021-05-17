/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.hypothesis;

import com.google.auto.service.AutoService;
import com.powsybl.commons.extensions.ExtensionXmlSerializer;
import com.powsybl.commons.xml.XmlReaderContext;
import com.powsybl.commons.xml.XmlUtil;
import com.powsybl.commons.xml.XmlWriterContext;
import com.powsybl.iidm.network.Generator;

import javax.xml.stream.XMLStreamException;
import java.io.InputStream;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
@AutoService(ExtensionXmlSerializer.class)
public class GeneratorStartupXmlSerializer implements ExtensionXmlSerializer<Generator, GeneratorStartup> {

    @Override
    public String getExtensionName() {
        return "startup";
    }

    @Override
    public String getCategoryName() {
        return "network";
    }

    @Override
    public Class<? super GeneratorStartup> getExtensionClass() {
        return GeneratorStartup.class;
    }

    @Override
    public boolean hasSubElements() {
        return false;
    }

    @Override
    public InputStream getXsdAsStream() {
        return getClass().getResourceAsStream("/xsd/generatorStartup.xsd");
    }

    @Override
    public String getNamespaceUri() {
        return "http://www.powsybl.org/schema/iidm/ext/generator_startup/1_0";
    }

    @Override
    public String getNamespacePrefix() {
        return "gs";
    }

    @Override
    public void write(GeneratorStartup startup, XmlWriterContext context) throws XMLStreamException {
        XmlUtil.writeDouble("predefinedActivePowerSetpoint", startup.getPredefinedActivePowerSetpoint(), context.getWriter());
        XmlUtil.writeDouble("startUpCost", startup.getStartUpCost(), context.getWriter());
        XmlUtil.writeDouble("marginalCost", startup.getMarginalCost(), context.getWriter());
        XmlUtil.writeDouble("plannedOutageRate", startup.getPlannedOutageRate(), context.getWriter());
        XmlUtil.writeDouble("forcedOutageRate", startup.getForcedOutageRate(), context.getWriter());
    }

    @Override
    public GeneratorStartup read(Generator generator, XmlReaderContext context) throws XMLStreamException {
        double predefinedActivePowerSetpoint = XmlUtil.readOptionalDoubleAttribute(context.getReader(), "predefinedActivePowerSetpoint");
        double startUpCost = XmlUtil.readOptionalDoubleAttribute(context.getReader(), "startUpCost");
        double marginalCost = XmlUtil.readOptionalDoubleAttribute(context.getReader(), "marginalCost");
        double plannedOutageRate = XmlUtil.readOptionalDoubleAttribute(context.getReader(), "plannedOutageRate");
        double forcedOutageRate = XmlUtil.readOptionalDoubleAttribute(context.getReader(), "forcedOutageRate");
        generator.newExtension(GeneratorStartupAdder.class)
                .withPredefinedActivePowerSetpoint(predefinedActivePowerSetpoint)
                .withStartUpCost(startUpCost)
                .withMarginalCost(marginalCost)
                .withPlannedOutageRate(plannedOutageRate)
                .withForcedOutageRate(forcedOutageRate)
                .add();
        return generator.getExtension(GeneratorStartup.class);
    }
}
