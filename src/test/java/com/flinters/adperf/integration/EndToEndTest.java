package com.flinters.adperf.integration;

import com.flinters.adperf.aggregator.Aggregator;
import com.flinters.adperf.aggregator.AggregationResult;
import com.flinters.adperf.writer.CsvWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EndToEndTest {

    @TempDir
    Path tempDir;

    @Test
    void goldenPath_top10CtrCorrectOrder() throws IOException {
        // 15 campaigns with CTR = i/100: CMP15 highest, CMP01 lowest
        File inputCsv = inputFile();
        File outputDir = outputDir();
        try (PrintWriter pw = new PrintWriter(inputCsv)) {
            pw.println("campaign_id,date,impressions,clicks,spend,conversions");
            for (int i = 1; i <= 15; i++) {
                pw.printf("CMP%02d,2024-01-01,10000,%d,%.2f,%d%n", i, i * 100, (double)(i * 50), i);
            }
        }

        runPipeline(inputCsv, outputDir);

        List<String> lines = readLines(outputDir, "top10_ctr.csv");
        assertEquals(11, lines.size(), "header + 10 data rows");
        assertTrue(lines.get(1).startsWith("CMP15,"), "highest CTR first");
        assertTrue(lines.get(10).startsWith("CMP06,"), "10th highest CTR last");
    }

    @Test
    void goldenPath_top10CpaCorrectOrder() throws IOException {
        // 15 campaigns: CPA = i * 10 (spend = i*100, conversions = 10) → CMP01 cheapest
        File inputCsv = inputFile();
        File outputDir = outputDir();
        try (PrintWriter pw = new PrintWriter(inputCsv)) {
            pw.println("campaign_id,date,impressions,clicks,spend,conversions");
            for (int i = 1; i <= 15; i++) {
                pw.printf("CMP%02d,2024-01-01,10000,100,%.2f,10%n", i, (double)(i * 100));
            }
        }

        runPipeline(inputCsv, outputDir);

        List<String> lines = readLines(outputDir, "top10_cpa.csv");
        assertEquals(11, lines.size(), "header + 10 data rows");
        assertTrue(lines.get(1).startsWith("CMP01,"), "lowest CPA first");
        assertTrue(lines.get(10).startsWith("CMP10,"), "10th lowest CPA last");
    }

    @Test
    void allZeroConversions_cpaFileHasHeaderOnly() throws IOException {
        File inputCsv = inputFile();
        File outputDir = outputDir();
        try (PrintWriter pw = new PrintWriter(inputCsv)) {
            pw.println("campaign_id,date,impressions,clicks,spend,conversions");
            for (int i = 1; i <= 5; i++) {
                pw.printf("CMP%02d,2024-01-01,10000,100,500.0,0%n", i);
            }
        }

        runPipeline(inputCsv, outputDir);

        List<String> lines = readLines(outputDir, "top10_cpa.csv");
        assertEquals(1, lines.size(), "header only when no conversions");
    }

    @Test
    void allZeroImpressions_allCtrIsZero() throws IOException {
        File inputCsv = inputFile();
        File outputDir = outputDir();
        try (PrintWriter pw = new PrintWriter(inputCsv)) {
            pw.println("campaign_id,date,impressions,clicks,spend,conversions");
            for (int i = 1; i <= 5; i++) {
                pw.printf("CMP%02d,2024-01-01,0,0,500.0,1%n", i);
            }
        }

        runPipeline(inputCsv, outputDir);

        List<String> lines = readLines(outputDir, "top10_ctr.csv");
        assertEquals(6, lines.size());
        for (int i = 1; i <= 5; i++) {
            String[] cols = lines.get(i).split(",");
            assertEquals("0.000000", cols[5], "CTR must be 0.000000 when impressions=0");
        }
    }

    @Test
    void fewerThan10Campaigns_outputsAllCampaigns() throws IOException {
        File inputCsv = inputFile();
        File outputDir = outputDir();
        try (PrintWriter pw = new PrintWriter(inputCsv)) {
            pw.println("campaign_id,date,impressions,clicks,spend,conversions");
            for (int i = 1; i <= 5; i++) {
                pw.printf("CMP%02d,2024-01-01,10000,%d,500.0,1%n", i, i * 100);
            }
        }

        runPipeline(inputCsv, outputDir);

        assertEquals(6, readLines(outputDir, "top10_ctr.csv").size(), "header + 5");
        assertEquals(6, readLines(outputDir, "top10_cpa.csv").size(), "header + 5");
    }

    @Test
    void mixedValidAndMalformedRows_correctAggregation() throws IOException {
        File inputCsv = inputFile();
        File outputDir = outputDir();
        try (PrintWriter pw = new PrintWriter(inputCsv)) {
            pw.println("campaign_id,date,impressions,clicks,spend,conversions");
            pw.println("CMP01,2024-01-01,10000,500,1000.0,10");
            pw.println("MALFORMED_ROW");
            pw.println("CMP01,2024-01-02,5000,250,500.0,5");
            pw.println("BAD,DATA");
            pw.println("CMP02,2024-01-01,8000,400,800.0,8");
        }

        AggregationResult result = new Aggregator().aggregate(inputCsv);

        assertEquals(3, result.processedRows());
        assertEquals(2, result.skippedRows());
        assertEquals(15000, result.stats().get("CMP01").totalImpressions);
        assertEquals(750, result.stats().get("CMP01").totalClicks);
    }

    @Test
    void singleDataRow_appearsInBothOutputs() throws IOException {
        File inputCsv = inputFile();
        File outputDir = outputDir();
        try (PrintWriter pw = new PrintWriter(inputCsv)) {
            pw.println("campaign_id,date,impressions,clicks,spend,conversions");
            pw.println("CMP01,2024-01-01,10000,500,1000.0,10");
        }

        runPipeline(inputCsv, outputDir);

        assertEquals(2, readLines(outputDir, "top10_ctr.csv").size(), "header + 1");
        assertEquals(2, readLines(outputDir, "top10_cpa.csv").size(), "header + 1");
    }

    @Test
    void emptyFile_headerOnly_zeroProcessedRows() throws IOException {
        File inputCsv = inputFile();
        try (PrintWriter pw = new PrintWriter(inputCsv)) {
            pw.println("campaign_id,date,impressions,clicks,spend,conversions");
        }

        AggregationResult result = new Aggregator().aggregate(inputCsv);

        assertEquals(0, result.processedRows());
        assertTrue(result.stats().isEmpty());
    }

    @Test
    void largeCampaignCount_outputsExactlyTop10() throws IOException {
        File inputCsv = tempDir.resolve("large.csv").toFile();
        File outputDir = outputDir();
        try (PrintWriter pw = new PrintWriter(inputCsv)) {
            pw.println("campaign_id,date,impressions,clicks,spend,conversions");
            for (int i = 1; i <= 50000; i++) {
                pw.printf("CMP%05d,2024-01-01,10000,%d,%.2f,1%n", i, i % 10000, (double) i);
            }
        }

        AggregationResult result = new Aggregator().aggregate(inputCsv);
        assertEquals(50000, result.processedRows());
        assertEquals(50000, result.stats().size());

        new CsvWriter(outputDir).writeTopCtr(result.stats().values());
        new CsvWriter(outputDir).writeTopCpa(result.stats().values());

        assertEquals(11, readLines(outputDir, "top10_ctr.csv").size(), "header + 10");
        assertEquals(11, readLines(outputDir, "top10_cpa.csv").size(), "header + 10");
    }

    private void runPipeline(File inputCsv, File outputDir) throws IOException {
        AggregationResult result = new Aggregator().aggregate(inputCsv);
        CsvWriter writer = new CsvWriter(outputDir);
        writer.writeTopCtr(result.stats().values());
        writer.writeTopCpa(result.stats().values());
    }

    private File inputFile() {
        return tempDir.resolve("input.csv").toFile();
    }

    private File outputDir() {
        File dir = tempDir.resolve("output").toFile();
        dir.mkdir();
        return dir;
    }

    private List<String> readLines(File outputDir, String filename) throws IOException {
        return Files.readAllLines(outputDir.toPath().resolve(filename));
    }
}
