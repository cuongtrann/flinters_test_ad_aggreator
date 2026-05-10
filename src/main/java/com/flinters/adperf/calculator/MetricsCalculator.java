package com.flinters.adperf.calculator;

public class MetricsCalculator {

    public static double computeCtr(long impressions, long clicks) {
        if (impressions == 0) return 0.0;
        return (double) clicks / impressions;
    }

    /**
     * Returns Double.NaN when conversions == 0 (no CPA defined).
     * Callers use Double.isNaN() to detect the undefined case.
     */
    public static double computeCpa(double spend, long conversions) {
        if (conversions == 0) return Double.NaN;
        return spend / conversions;
    }
}
