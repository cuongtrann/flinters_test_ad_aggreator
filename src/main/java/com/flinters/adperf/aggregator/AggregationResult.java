package com.flinters.adperf.aggregator;

import com.flinters.adperf.model.CampaignStats;

import java.util.Map;

public record AggregationResult(
    Map<String, CampaignStats> stats,
    long processedRows,
    long skippedRows
) {}
