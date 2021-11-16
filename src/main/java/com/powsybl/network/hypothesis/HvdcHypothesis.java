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
        createHvdc(generator1, getP(generator1), generator2, getP(generator2), HvdcConverterStation.HvdcType.VSC);
    }

    public static void convertLoadsToHvdc(Load load1, Load load2) {
        createHvdc(load1, load2);
        disconnectInjection(load1);
        disconnectInjection(load2);
    }

    public static void createHvdc(Load load1, Load load2) {
        createHvdc(load1, getP(load1), load2, getP(load2), HvdcConverterStation.HvdcType.LCC);
    }

    private static void disconnectInjection(Injection injection) {
        injection.getTerminal().disconnect();
    }

    private static void createHvdc(Injection injection1, double p1, Injection injection2, double p2, HvdcConverterStation.HvdcType hvdcType) {
        VoltageLevel vl1 = injection1.getTerminal().getVoltageLevel();
        VoltageLevel vl2 = injection2.getTerminal().getVoltageLevel();

        HvdcLine.ConvertersMode converterMode = converterMode(p1, p2);

        double poleLossP1 = getPoleLossP(p1, p2, converterMode);
        double poleLossP2 = getPoleLossP(p1, p2, converterMode);
        double lossFactor1 = getLossFactor1(p1, poleLossP1, converterMode);
        double lossFactor2 = getLossFactor2(p2, poleLossP2, converterMode);

        HvdcConverterStation converterStation1;
        HvdcConverterStation converterStation2;
        if (hvdcType.equals(HvdcConverterStation.HvdcType.VSC)) {
            converterStation1 = createVscConverterStation(vl1, (Generator) injection1, lossFactor1);
            converterStation2 = createVscConverterStation(vl2, (Generator) injection2, lossFactor2);
        } else if (hvdcType.equals(HvdcConverterStation.HvdcType.LCC)) {
            converterStation1 = createLccConverterStation(vl1, (Load) injection1, lossFactor1);
            converterStation2 = createLccConverterStation(vl2, (Load) injection2, lossFactor2);
        } else {
            throw new AssertionError();
        }
        Network network = injection1.getNetwork();
        double activePowerSetpoint = getActivePowerSetpoint(p1, p2, poleLossP1, poleLossP2, converterMode);
        HvdcLineAdder adder = network.newHvdcLine()
                .setId(injection1.getId() + "_" + injection2.getId() + "_HVDC")
                .setConvertersMode(converterMode)
                .setConverterStationId1(converterStation1.getId())
                .setConverterStationId2(converterStation2.getId())
                .setNominalV(vl1.getNominalV())
                .setActivePowerSetpoint(activePowerSetpoint)
                .setMaxP(getDefaultMaxP(activePowerSetpoint))
                .setR(getDefaultR(vl1.getNominalV()));

        adder.add();
    }

    private static HvdcConverterStation createLccConverterStation(VoltageLevel vl, Load load, double lossFactor) {
        LccConverterStationAdder converterStationAdder = vl.newLccConverterStation()
                .setId(load.getId() + "_LCC")
                .setLossFactor((float) lossFactor)
                .setPowerFactor((float) getPowerFactor(load.getP0(), load.getQ0()));

        attachConverter(load.getTerminal(), converterStationAdder, (bus, adder) -> adder.setConnectableBus(bus.getId()), (bus, adder) -> adder.setBus(bus.getId()), (node, adder) -> adder.setNode(node));
        return converterStationAdder.add();
    }

    private static VscConverterStation createVscConverterStation(VoltageLevel vl, Generator generator, double lossFactor) {
        VscConverterStationAdder converterStationAdder = vl.newVscConverterStation()
                .setId(generator.getId() + "_VSC")
                .setLossFactor((float) lossFactor)
                .setVoltageSetpoint(generator.getTargetV())
                .setReactivePowerSetpoint(generator.getTargetQ())
                .setRegulatingTerminal(generator.getRegulatingTerminal())
                .setVoltageRegulatorOn(generator.isVoltageRegulatorOn());

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
            int converterNode = createConverterNode(terminal);
            terminal.getVoltageLevel().getNodeBreakerView().newInternalConnection()
                    .setNode1(node)
                    .setNode2(converterNode)
                    .add();
            nodeSetter.accept(converterNode, adder);
        } else {
            throw new AssertionError();
        }
    }

    private static int createConverterNode(Terminal terminal) {
        return terminal.getVoltageLevel().getNodeBreakerView().getMaximumNodeIndex() + 1;
    }

    private static double getP(Load load) {
        return load.getP0();
    }

    private static double getP(Generator generator) {
        return generator.getTargetP();
    }

    private static HvdcLine.ConvertersMode converterMode(double p1, double p2) {
        if (p1 > 0.0 && p2 < 0.0) {
            return HvdcLine.ConvertersMode.SIDE_1_RECTIFIER_SIDE_2_INVERTER;
        }
        if (p1 < 0.0 && p2 > 0.0) {
            return HvdcLine.ConvertersMode.SIDE_1_INVERTER_SIDE_2_RECTIFIER;
        }
        if (p1 < 0.0 && p2 < 0.0) {
            throw new AssertionError();
        }
        if (p1 > 0.0 && p2 > 0.0) {
            throw new AssertionError();
        }
        return HvdcLine.ConvertersMode.SIDE_1_RECTIFIER_SIDE_2_INVERTER;
    }

    private static double getDefaultR(double vNominal) {
        double defaultR = 0.01;
        double sb = 100.0;

        return defaultR * vNominal * vNominal / sb;
    }

    private static double getDefaultMaxP(double activeSetpoint) {
        return activeSetpoint * 1.2;
    }

    private static double getMaxP(double gen1MaxP, double gen2MaxP, double activeSetpoint) {
        double maxP = gen1MaxP;
        if (gen2MaxP > maxP) {
            maxP = gen2MaxP;
        }
        if (activeSetpoint > maxP) {
            maxP = activeSetpoint * 1.2;
        }
        return maxP;
    }

    private static double getPoleLossP(double p1, double p2, HvdcLine.ConvertersMode mode) {
        if (mode.equals(HvdcLine.ConvertersMode.SIDE_1_RECTIFIER_SIDE_2_INVERTER)) {
            return getPoleLossPrectifier(p1, p2);
        } else if (mode.equals(HvdcLine.ConvertersMode.SIDE_1_INVERTER_SIDE_2_RECTIFIER)) {
            return getPoleLossPinverter(p1, p2);
        }
        return 0.0;
    }

    // Same losses at both ends
    private static double getPoleLossPrectifier(double pRectifier, double pInverter) {
        return calculatePoleLossP(pRectifier, pInverter, 0.5);
    }

    private static double getPoleLossPinverter(double pInverter, double pRectifier) {
        return calculatePoleLossP(pRectifier, pInverter, 1.0 - 0.5);
    }

    private static double calculatePoleLossP(double pRectifier, double pInverter, double factor) {
        return (pRectifier - pInverter) * factor;
    }

    private static double getPowerFactor(double activePower, double reactivePower) {
        return activePower / Math.hypot(activePower, reactivePower);
    }

    private static double getLossFactor1(double pAC1, double poleLossP1, HvdcLine.ConvertersMode mode) {
        if (mode.equals(HvdcLine.ConvertersMode.SIDE_1_RECTIFIER_SIDE_2_INVERTER)) { // pAC1 > 0
            return poleLossP1 / pAC1 * 100;
        } else if (mode.equals(HvdcLine.ConvertersMode.SIDE_1_INVERTER_SIDE_2_RECTIFIER) && Math.abs(pAC1) + poleLossP1 != 0) { // pAC1 < 0
            return poleLossP1 / (Math.abs(pAC1) + poleLossP1) * 100;
        }
        return 0.0;
    }

    private static double getLossFactor2(double pAC2, double poleLossP2, HvdcLine.ConvertersMode mode) {
        if (mode.equals(HvdcLine.ConvertersMode.SIDE_1_INVERTER_SIDE_2_RECTIFIER)) { // pAC2 > 0
            return poleLossP2 / pAC2 * 100;
        } else if (mode.equals(HvdcLine.ConvertersMode.SIDE_1_RECTIFIER_SIDE_2_INVERTER) && Math.abs(pAC2) + poleLossP2 != 0) { // pAC2 < 0
            return poleLossP2 / (Math.abs(pAC2) + poleLossP2) * 100;
        }
        return 0.0;
    }

    private static double getActivePowerSetpoint(double p1, double p2, double poleLossP1, double poleLossP2, HvdcLine.ConvertersMode converterMode) {
        if (converterMode.equals(HvdcLine.ConvertersMode.SIDE_1_RECTIFIER_SIDE_2_INVERTER)) {
            return p1 - poleLossP1;
        } else if (converterMode.equals(HvdcLine.ConvertersMode.SIDE_1_INVERTER_SIDE_2_RECTIFIER)) {
            return p2 - poleLossP2;
        }
        return 0.0;
    }

    private HvdcHypothesis() {
    }
}