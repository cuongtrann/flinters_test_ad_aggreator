package com.flinters.adperf.calculator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MetricsCalculatorTest {

    @Test
    void normalCtr() {
        assertEquals(0.05, MetricsCalculator.computeCtr(1000, 50), 1e-9);
    }

    @Test
    void zeroImpressions_ctrIsZero() {
        assertEquals(0.0, MetricsCalculator.computeCtr(0, 0));
    }

    @Test
    void ctrGreaterThanOne_notClamped() {
        assertEquals(2.0, MetricsCalculator.computeCtr(100, 200), 1e-9);
    }

    @Test
    void normalCpa() {
        assertEquals(50.0, MetricsCalculator.computeCpa(500.0, 10), 1e-9);
    }

    @Test
    void zeroConversions_cpaIsNaN() {
        assertTrue(Double.isNaN(MetricsCalculator.computeCpa(500.0, 0)));
    }

    @Test
    void zeroSpendPositiveConversions_cpaIsZero() {
        assertEquals(0.0, MetricsCalculator.computeCpa(0.0, 5), 1e-9);
    }

    @Test
    void cpaPassesThroughDoubleArithmetic() {
        // verify computeCpa introduces no additional rounding beyond the input
        double spend = 0.1 + 0.2; // 0.30000000000000004 in IEEE 754 — intentionally imprecise input
        assertEquals(spend, MetricsCalculator.computeCpa(spend, 1), 0.0);
    }
}
