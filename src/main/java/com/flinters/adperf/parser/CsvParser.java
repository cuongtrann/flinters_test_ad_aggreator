package com.flinters.adperf.parser;

import java.util.Optional;

public class CsvParser {
    private static final int COL_COUNT = 6;
    private static final int WARN_LIMIT = 100;

    private int warnCount = 0;

    public Optional<ParsedRow> parseLine(String line, long lineNum) {
        if (line.startsWith("campaign_id")) {
            return Optional.empty();
        }

        String[] cols = line.split(",", -1);
        if (cols.length != COL_COUNT) {
            emitWarn(lineNum, "expected " + COL_COUNT + " columns, got " + cols.length + ". Skipping.");
            return Optional.empty();
        }

        try {
            String campaignId = cols[0].trim();
            long impressions = parseLong(cols[2], "impressions");
            long clicks      = parseLong(cols[3], "clicks");
            double spend     = parseDouble(cols[4], "spend");
            long conversions = parseLong(cols[5], "conversions");
            return Optional.of(new ParsedRow(campaignId, impressions, clicks, spend, conversions));
        } catch (NumberFormatException e) {
            emitWarn(lineNum, e.getMessage() + ". Skipping.");
            return Optional.empty();
        }
    }

    public int getWarnCount() {
        return warnCount;
    }

    public boolean hasSuppressedWarnings() {
        return warnCount > WARN_LIMIT;
    }

    private static long parseLong(String val, String field) {
        try {
            return Long.parseLong(val.trim());
        } catch (NumberFormatException e) {
            throw new NumberFormatException(
                "invalid value \"" + val.trim() + "\" in column \"" + field + "\"");
        }
    }

    private static double parseDouble(String val, String field) {
        try {
            return Double.parseDouble(val.trim());
        } catch (NumberFormatException e) {
            throw new NumberFormatException(
                "invalid value \"" + val.trim() + "\" in column \"" + field + "\"");
        }
    }

    private void emitWarn(long lineNum, String msg) {
        warnCount++;
        if (warnCount <= WARN_LIMIT) {
            System.err.println("[WARN] Row " + lineNum + ": " + msg);
        } else if (warnCount == WARN_LIMIT + 1) {
            System.err.println("[WARN] Further row warnings suppressed. See final summary for total count.");
        }
    }
}
