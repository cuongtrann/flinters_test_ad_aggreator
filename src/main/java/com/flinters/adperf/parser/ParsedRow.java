package com.flinters.adperf.parser;

public record ParsedRow(
    String campaignId,
    long impressions,
    long clicks,
    double spend,
    long conversions
) {}
