package com.flinters.adperf.aggregator;

import com.flinters.adperf.model.CampaignStats;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AggregatorTest {

    @TempDir
    Path tempDir;

    @Test
    void singleCampaignSingleRow() throws IOException {
        File csv = writeCsv("campaign_id,date,impressions,clicks,spend,conversions\n",
            "CMP01,2024-01-01,1000,50,200.0,5\n");

        AggregationResult result = new Aggregator().aggregate(csv);

        assertEquals(1, result.processedRows());
        assertEquals(0, result.skippedRows());

        CampaignStats s = result.stats().get("CMP01");
        assertNotNull(s);
        assertEquals(1000, s.totalImpressions);
        assertEquals(50, s.totalClicks);
        assertEquals(200.0, s.totalSpend, 1e-9);
        assertEquals(5, s.totalConversions);
    }

    @Test
    void singleCampaignMultipleRows_accumulatesCorrectly() throws IOException {
        File csv = writeCsv("campaign_id,date,impressions,clicks,spend,conversions\n",
            "CMP01,2024-01-01,1000,50,200.0,5\n",
            "CMP01,2024-01-02,2000,100,400.0,10\n");

        AggregationResult result = new Aggregator().aggregate(csv);

        assertEquals(2, result.processedRows());
        CampaignStats s = result.stats().get("CMP01");
        assertEquals(3000, s.totalImpressions);
        assertEquals(150, s.totalClicks);
        assertEquals(600.0, s.totalSpend, 1e-9);
        assertEquals(15, s.totalConversions);
    }

    @Test
    void multipleCampaigns_accumulatedIndependently() throws IOException {
        File csv = writeCsv("campaign_id,date,impressions,clicks,spend,conversions\n",
            "CMP01,2024-01-01,1000,50,200.0,5\n",
            "CMP02,2024-01-01,500,25,100.0,2\n");

        AggregationResult result = new Aggregator().aggregate(csv);

        assertEquals(2, result.processedRows());
        assertEquals(2, result.stats().size());
        assertEquals(1000, result.stats().get("CMP01").totalImpressions);
        assertEquals(500, result.stats().get("CMP02").totalImpressions);
    }

    @Test
    void malformedRow_skippedAndCounted() throws IOException {
        File csv = writeCsv("campaign_id,date,impressions,clicks,spend,conversions\n",
            "CMP01,2024-01-01,1000,50,200.0,5\n",
            "BAD_ROW\n",
            "CMP02,2024-01-01,500,25,100.0,2\n");

        AggregationResult result = new Aggregator().aggregate(csv);

        assertEquals(2, result.processedRows());
        assertEquals(1, result.skippedRows());
        assertEquals(2, result.stats().size());
    }

    @Test
    void tenThousandRows_100Campaigns_correctTotals() throws IOException {
        File csv = tempDir.resolve("big.csv").toFile();
        try (PrintWriter pw = new PrintWriter(csv)) {
            pw.println("campaign_id,date,impressions,clicks,spend,conversions");
            for (int i = 0; i < 10000; i++) {
                int campaign = (i % 100) + 1;
                pw.printf("CMP%03d,2024-01-01,100,10,50.0,1%n", campaign);
            }
        }

        AggregationResult result = new Aggregator().aggregate(csv);

        assertEquals(10000, result.processedRows());
        assertEquals(100, result.stats().size());
        // Each of 100 campaigns has 100 rows: 100 rows × 100 impressions = 10000
        for (CampaignStats s : result.stats().values()) {
            assertEquals(10000, s.totalImpressions);
            assertEquals(1000, s.totalClicks);
            assertEquals(5000.0, s.totalSpend, 1e-9);
            assertEquals(100, s.totalConversions);
        }
    }

    private File writeCsv(String... lines) throws IOException {
        File f = tempDir.resolve("test.csv").toFile();
        try (PrintWriter pw = new PrintWriter(f)) {
            for (String line : lines) pw.print(line);
        }
        return f;
    }
}
