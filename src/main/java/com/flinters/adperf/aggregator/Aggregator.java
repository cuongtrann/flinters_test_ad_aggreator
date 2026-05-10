package com.flinters.adperf.aggregator;

import com.flinters.adperf.model.CampaignStats;
import com.flinters.adperf.parser.CsvParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

public class Aggregator {

    public AggregationResult aggregate(File inputFile) throws IOException {
        Map<String, CampaignStats> stats = new HashMap<>(8192);
        CsvParser parser = new CsvParser();
        long lineNum = 0;
        long processedRows = 0;

        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(Files.newInputStream(inputFile.toPath()), StandardCharsets.UTF_8),
            256 * 1024)) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                var parsed = parser.parseLine(line, lineNum);
                if (parsed.isEmpty()) continue;

                var r = parsed.get();
                stats.computeIfAbsent(r.campaignId(), CampaignStats::new)
                     .accumulate(r.impressions(), r.clicks(), r.spend(), r.conversions());
                processedRows++;
            }
        }

        int skipped = parser.getWarnCount();
        if (parser.hasSuppressedWarnings()) {
            System.err.printf("[WARN] %,d rows skipped due to parse errors.%n", skipped);
        }

        return new AggregationResult(stats, processedRows, skipped);
    }
}
