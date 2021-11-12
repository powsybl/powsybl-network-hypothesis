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
 * @author Marcos de Miguel <demiguelm at aia.es>
 */
public final class HvdcHypothesis {

    private static final double DEFAULT_POWER_FACTOR = 0.8;

    public static void convertGeneratorsToHvdc(Generator generator1, Generator generator2) {
        createHvdc(generator1, generator2);
        disconnectInjection(generator1);
        disconnectInjection(generator2);
    }

    public static void createHvdc(Generator generator1, Generator generator2) {
        createHvdc(generator1, generator2, HvdcConverterStation.HvdcType.VSC);
    }

    public static void convertLoadsToHvdc(Load load1, Load load2) {
        createHvdc(load1, load2);
        disconnectInjection(load1);
        disconnectInjection(load2);
    }

    public static void createHvdc(Load load1, Load load2) {
        createHvdc(load1, load2, HvdcConverterStation.HvdcType.LCC);
    }

    private static void disconnectInjection(Injection injection) {
        injection.getTerminal().disconnect();
    }

    private static void createHvdc(Injection injection1, Injection injection2, HvdcConverterStation.HvdcType hvdcType) {
        VoltageLevel vl1 = injection1.getTerminal().getVoltageLevel();
        VoltageLevel vl2 = injection2.getTerminal().getVoltageLevel();
        HvdcConverterStation converterStation1;
        HvdcConverterStation converterStation2;
        if (hvdcType.equals(HvdcConverterStation.HvdcType.VSC)) {
            converterStation1 = createVscConverterStation(vl1, (Generator) injection1);
            converterStation2 = createVscConverterStation(vl2, (Generator) injection2);
        } else if (hvdcType.equals(HvdcConverterStation.HvdcType.LCC)) {
            converterStation1 = createLccConverterStation(vl1, (Load) injection1);
            converterStation2 = createLccConverterStation(vl2, (Load) injection2);
        } else {
            throw new AssertionError();
        }
        Network network = injection1.getNetwork();
        HvdcLineAdder adder = network.newHvdcLine()
                .setId(injection1.getId() + "_" + injection2.getId() + "_HVDC")
                .setConvertersMode(HvdcLine.ConvertersMode.SIDE_1_RECTIFIER_SIDE_2_INVERTER)
                .setConverterStationId1(converterStation1.getId())
                .setConverterStationId2(converterStation2.getId())
                .setNominalV(vl1.getNominalV())
                .setActivePowerSetpoint(0.0)
                .setMaxP(0.0)
                .setR(0.1);

        adder.add();
    }

    private static HvdcConverterStation createLccConverterStation(VoltageLevel vl, Load load) {
        LccConverterStationAdder converterStationAdder = vl.newLccConverterStation()
                .setId(load.getId() + "_LCC")
                .setLossFactor(0.0f)
                .setPowerFactor((float) DEFAULT_POWER_FACTOR);

        attachConverter(load.getTerminal(), converterStationAdder, (bus, adder) -> adder.setConnectableBus(bus.getId()), (bus, adder) -> adder.setBus(bus.getId()), (node, adder) -> adder.setNode(node));
        return converterStationAdder.add();
    }

    private static VscConverterStation createVscConverterStation(VoltageLevel vl, Generator generator) {
        VscConverterStationAdder converterStationAdder = vl.newVscConverterStation()
                .setId(generator.getId() + "_VSC")
                .setLossFactor(0.0f)
                .setVoltageRegulatorOn(generator.isVoltageRegulatorOn())
                .setVoltageSetpoint(generator.getTargetV())
                .setReactivePowerSetpoint(generator.getTargetQ());

        attachConverter(generator.getTerminal(), converterStationAdder, (bus, adder) -> adder.setConnectableBus(bus.getId()), (bus, adder) -> adder.setBus(bus.getId()), (node, adder) -> adder.setNode(node));
        VscConverterStation converterStation = converterStationAdder.add();
        addReactiveLimits(converterStation, generator);
        return converterStation;
    }

    private static void addReactiveLimits(VscConverterStation converterStation, Generator generator) {
        ReactiveLimits reactiveLimits = generator.getReactiveLimits();
        if (reactiveLimits == null) {
            return;
        }

        if (reactiveLimits.getKind() == ReactiveLimitsKind.MIN_MAX) {
            converterStation.newMinMaxReactiveLimits()
                    .setMinQ(((MinMaxReactiveLimits) reactiveLimits).getMinQ())
                    .setMaxQ(((MinMaxReactiveLimits) reactiveLimits).getMaxQ())
                    .add();
        } else if (reactiveLimits.getKind() == ReactiveLimitsKind.CURVE) {
            ReactiveCapabilityCurve curve = (ReactiveCapabilityCurve) reactiveLimits;
            ReactiveCapabilityCurveAdder adder = converterStation.newReactiveCapabilityCurve();
            for (ReactiveCapabilityCurve.Point point : curve.getPoints()) {
                adder.beginPoint()
                        .setP(point.getP())
                        .setMinQ(point.getMinQ())
                        .setMaxQ(point.getMaxQ())
                        .endPoint();
            }
            adder.add();
        } else {
            throw new AssertionError();
        }
    }

    private static void attachConverter(Terminal terminal, HvdcConverterStationAdder adder, BiConsumer<Bus, HvdcConverterStationAdder> connectableBusSetter,
                                        BiConsumer<Bus, HvdcConverterStationAdder> busSetter, BiConsumer<Integer, HvdcConverterStationAdder> nodeSetter) {
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

    private HvdcHypothesis() {
    }
}
