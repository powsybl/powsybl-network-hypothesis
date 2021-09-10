/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.hypothesis;

import com.powsybl.iidm.network.*;

import java.util.function.BiConsumer;

/**
 * Some useful utility methods to create network hypotheses.
 *
 * @author Miora Ralambotiana <miora.ralambotiana at rte-france.com>
 */
public final class TopologyHypothesisUtils {

    /**
     * Split a given line and create a fictitious voltage level at the junction.<br>
     * The line is considered split in half such as this:<br>
     * <code>r1 = 0.5 * r</code><br>
     * <code>r2 = 0.5 * r</code><br>
     * If the line is between two BUS-BREAKER voltage levels, the fictitious voltage level will be BUS-BREAKER and the two new lines
     * will be linked to a fictitious bus in this voltage level.<br>
     * The created voltage level will be NODE_BREAKER.
     */
    public static void createVoltageLevelOnLine(Line line) {
        createVoltageLevelOnLine(line, false);
    }

    /**
     * Split a given line and create a fictitious voltage level at the junction.<br>
     * The line is considered split in half such as this:<br>
     * <code>r1 = 0.5 * r</code><br>
     * <code>r2 = 0.5 * r</code><br>
     * If the line is between two BUS-BREAKER voltage levels, the fictitious voltage level will be BUS-BREAKER and the two new lines
     * will be linked to a fictitious bus in this voltage level.<br>
     * The created voltage level will be NODE_BREAKER.<br>
     * If <code>withBreakers</code> is <code>true</code>, two closed switches connect the created lines. Otherwise, an internal connection connect the created lines in NODE_BREAKER and
     * they are connected to the same bus in BUS_BREAKER.
     */
    public static void createVoltageLevelOnLine(Line line, boolean withBreakers) {
        createVoltageLevelOnLine(line, TopologyKind.NODE_BREAKER, withBreakers);
    }

    /**
     * Split a given line and create a fictitious voltage level at the junction.<br>
     * The line is considered split in half such as this:<br>
     * <code>r1 = 0.5 * r</code><br>
     * <code>r2 = 0.5 * r</code><br>
     * If the line is between two BUS-BREAKER voltage levels, the fictitious voltage level will be BUS-BREAKER and the two new lines
     * will be linked to a fictitious bus in this voltage level.<br>
     * The created voltage level will have the given topology kind.
     */
    public static void createVoltageLevelOnLine(Line line, TopologyKind topologyKind) {
        createVoltageLevelOnLine(line, topologyKind, false);
    }

    /**
     * Split a given line and create a fictitious voltage level at the junction.<br>
     * The line is considered split in half such as this:<br>
     * <code>r1 = 0.5 * r</code><br>
     * <code>r2 = 0.5 * r</code><br>
     * If the line is between two BUS-BREAKER voltage levels, the fictitious voltage level will be BUS-BREAKER and the two new lines
     * will be linked to a fictitious bus in this voltage level.<br>
     * The created voltage level will have the given topology kind.<br>
     * If <code>withBreakers</code> is <code>true</code>, two closed switches connect the created lines. Otherwise, an internal connection connect the created lines in NODE_BREAKER and
     * they are connected to the same bus in BUS_BREAKER.
     */
    public static void createVoltageLevelOnLine(Line line, TopologyKind topologyKind, boolean withBreakers) {
        createVoltageLevelOnLine(0.5, 0.5, 0.5, 0.5, 0.5, 0.5, line, topologyKind, withBreakers);
    }

    /**
     * Split a given line and create a fictitious voltage level at the junction.<br>
     * The characteristics of the two new lines respect the given ratios such as this:<br>
     * <code>r1 = rdp * r</code><br>
     * <code>r2 = (1 - rdp) * r</code><br>
     * If the line is between two BUS-BREAKER voltage levels, the fictitious voltage level will be BUS-BREAKER and the two new lines
     * will be linked to a fictitious bus in this voltage level.<br>
     * The created voltage level will have the given topology kind.
     */
    public static void createVoltageLevelOnLine(double rdp, double xdp, double g1dp, double b1dp, double g2dp, double b2dp,
                                                Line line, TopologyKind topologyKind) {
        createVoltageLevelOnLine(rdp, xdp, g1dp, b1dp, g2dp, b2dp, line, topologyKind, false);
    }

    /**
     * Split a given line and create a fictitious voltage level at the junction.<br>
     * The characteristics of the two new lines respect the given ratios such as this:<br>
     * <code>r1 = rdp * r</code><br>
     * <code>r2 = (1 - rdp) * r</code><br>
     * If the line is between two BUS-BREAKER voltage levels, the fictitious voltage level will be BUS-BREAKER and the two new lines
     * will be linked to a fictitious bus in this voltage level.<br>
     * The created voltage level will have the given topology kind.<br>
     * If <code>withBreakers</code> is <code>true</code>, two closed switches connect the created lines. Otherwise, an internal connection connect the created lines in NODE_BREAKER and
     * they are connected to the same bus in BUS_BREAKER.
     */
    public static void createVoltageLevelOnLine(double rdp, double xdp, double g1dp, double b1dp, double g2dp, double b2dp,
                                                Line line, TopologyKind topologyKind, boolean withBreakers) {
        Network network = line.getNetwork();
        Substation substation = network.newSubstation()
                .setId(line.getId() + "_SUBSTATION")
                .setEnsureIdUnicity(true)
                .setFictitious(true)
                .add();
        VoltageLevel voltageLevel = substation.newVoltageLevel()
                .setId(line.getId() + "_VL")
                .setEnsureIdUnicity(true)
                .setNominalV((line.getTerminal1().getVoltageLevel().getNominalV() + line.getTerminal2().getVoltageLevel().getNominalV()) / 2)
                .setHighVoltageLimit(Math.max(line.getTerminal1().getVoltageLevel().getHighVoltageLimit(), line.getTerminal2().getVoltageLevel().getHighVoltageLimit()))
                .setLowVoltageLimit(Math.min(line.getTerminal1().getVoltageLevel().getLowVoltageLimit(), line.getTerminal2().getVoltageLevel().getLowVoltageLimit()))
                .setTopologyKind(topologyKind)
                .setFictitious(true)
                .add();
        LineAdder adder1 = network.newLine()
                .setId(line.getId() + "_1")
                .setEnsureIdUnicity(true)
                .setVoltageLevel1(line.getTerminal1().getVoltageLevel().getId())
                .setVoltageLevel2(voltageLevel.getId())
                .setR(line.getR() * rdp)
                .setX(line.getX() * xdp)
                .setG1(line.getG1() * g1dp)
                .setB1(line.getB1() * b1dp)
                .setG2(line.getG2() * g2dp)
                .setB2(line.getB2() * b2dp);
        LineAdder adder2 = network.newLine()
                .setId(line.getId() + "_2")
                .setEnsureIdUnicity(true)
                .setVoltageLevel1(voltageLevel.getId())
                .setVoltageLevel2(line.getTerminal2().getVoltageLevel().getId())
                .setR(line.getR() * (1 - rdp))
                .setX(line.getX() * (1 - xdp))
                .setG1(line.getG1() * (1 - g1dp))
                .setB1(line.getB1() * (1 - b1dp))
                .setG2(line.getG2() * (1 - g2dp))
                .setB2(line.getB2() * (1 - b2dp));
        attachLine(line.getTerminal1(), adder1, (bus, adder) -> adder.setConnectableBus1(bus.getId()), (bus, adder) -> adder.setBus1(bus.getId()), (node, adder) -> adder.setNode1(node));
        attachLine(line.getTerminal2(), adder2, (bus, adder) -> adder.setConnectableBus2(bus.getId()), (bus, adder) -> adder.setBus2(bus.getId()), (node, adder) -> adder.setNode2(node));
        if (topologyKind == TopologyKind.BUS_BREAKER) {
            Bus bus1 = voltageLevel.getBusBreakerView()
                    .newBus()
                    .setId(line.getId() + "_BUS")
                    .setEnsureIdUnicity(true)
                    .setFictitious(true)
                    .add();
            adder1.setBus2(bus1.getId());
            if (withBreakers) {
                Bus bus2 = voltageLevel.getBusBreakerView()
                        .newBus()
                        .setId(line.getId() + "_BUS")
                        .setEnsureIdUnicity(true)
                        .setFictitious(true)
                        .add();
                Bus bus3 = voltageLevel.getBusBreakerView()
                        .newBus()
                        .setId(line.getId() + "_BUS")
                        .setEnsureIdUnicity(true)
                        .setFictitious(true)
                        .add();
                voltageLevel.getBusBreakerView()
                        .newSwitch()
                        .setId(line.getId() + "_SW")
                        .setEnsureIdUnicity(true)
                        .setFictitious(true)
                        .setOpen(false)
                        .setBus1(bus1.getId())
                        .setBus2(bus2.getId())
                        .add();
                voltageLevel.getBusBreakerView()
                        .newSwitch()
                        .setId(line.getId() + "_SW")
                        .setEnsureIdUnicity(true)
                        .setFictitious(true)
                        .setOpen(false)
                        .setBus1(bus2.getId())
                        .setBus2(bus3.getId())
                        .add();
                adder2.setBus1(bus3.getId());
            } else {
                adder2.setBus1(bus1.getId());
            }
        } else if (topologyKind == TopologyKind.NODE_BREAKER) {
            adder1.setNode2(0);
            if (withBreakers) {
                voltageLevel.getNodeBreakerView()
                        .newSwitch()
                        .setId(line.getId() + "_SW")
                        .setEnsureIdUnicity(true)
                        .setFictitious(true)
                        .setKind(SwitchKind.BREAKER)
                        .setOpen(false)
                        .setNode1(0)
                        .setNode2(1)
                        .add();
                voltageLevel.getNodeBreakerView()
                        .newSwitch()
                        .setId(line.getId() + "_SW")
                        .setEnsureIdUnicity(true)
                        .setFictitious(true)
                        .setKind(SwitchKind.BREAKER)
                        .setOpen(false)
                        .setNode1(1)
                        .setNode2(2)
                        .add();
                adder2.setNode1(2);
            } else {
                voltageLevel.getNodeBreakerView()
                        .newInternalConnection()
                        .setNode1(0)
                        .setNode2(1)
                        .add();
                adder2.setNode1(1);
            }
        } else {
            throw new AssertionError();
        }
        line.remove();
        adder1.add();
        adder2.add();
    }

    private static void attachLine(Terminal terminal, LineAdder adder, BiConsumer<Bus, LineAdder> connectableBusSetter,
                                   BiConsumer<Bus, LineAdder> busSetter, BiConsumer<Integer, LineAdder> nodeSetter) {
        if (terminal.getVoltageLevel().getTopologyKind() == TopologyKind.BUS_BREAKER) {
            connectableBusSetter.accept(terminal.getBusBreakerView().getConnectableBus(), adder);
            Bus bus = terminal.getBusBreakerView().getBus();
            if (bus != null) {
                busSetter.accept(bus, adder);
            }
        } else if (terminal.getVoltageLevel().getTopologyKind() == TopologyKind.NODE_BREAKER) {
            int node = terminal.getNodeBreakerView().getNode();
            nodeSetter.accept(node, adder);
        } else {
            throw new AssertionError();
        }
    }

    private TopologyHypothesisUtils() {
    }
}
