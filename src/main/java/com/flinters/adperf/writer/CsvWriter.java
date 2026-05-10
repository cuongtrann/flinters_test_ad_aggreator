package com.flinters.adperf.writer;

import com.flinters.adperf.calculator.MetricsCalculator;
import com.flinters.adperf.model.CampaignStats;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.PriorityQueue;

public class CsvWriter {
    private static final String HEADER =
        "campaign_id,total_impressions,total_clicks,total_spend,total_conversions,ctr,cpa";
    private static final int TOP_N = 10;

    private final File outputDir;

    public CsvWriter(File outputDir) {
        this.outputDir = outputDir;
    }

    public void writeTopCtr(Collection<CampaignStats> allStats) throws IOException {
        Comparator<CampaignStats> outputOrder = Comparator
            .comparingDouble((CampaignStats s) -> MetricsCalculator.computeCtr(s.totalImpressions, s.totalClicks))
            .reversed()
            .thenComparing(s -> s.campaignId);

        List<CampaignStats> ranked = selectTop(allStats, outputOrder.reversed(), outputOrder);

        if (ranked.size() < TOP_N) {
            System.out.printf("[WARN] Only %d unique campaigns found — top10_ctr.csv contains %d rows.%n",
                ranked.size(), ranked.size());
        }
        write(new File(outputDir, "top10_ctr.csv"), ranked);
    }

    public void writeTopCpa(Collection<CampaignStats> allStats) throws IOException {
        Comparator<CampaignStats> outputOrder = Comparator
            .comparingDouble((CampaignStats s) -> MetricsCalculator.computeCpa(s.totalSpend, s.totalConversions))
            .thenComparing(s -> s.campaignId);

        List<CampaignStats> eligible = allStats.stream()
            .filter(s -> s.totalConversions > 0)
            .toList();

        List<CampaignStats> ranked = selectTop(eligible, outputOrder.reversed(), outputOrder);

        if (ranked.isEmpty()) {
            System.out.println("[WARN] No campaigns with conversions > 0. top10_cpa.csv will contain header only.");
        } else if (ranked.size() < TOP_N) {
            System.out.printf("[WARN] Only %d campaigns have conversions > 0 — top10_cpa.csv contains %d rows.%n",
                ranked.size(), ranked.size());
        }
        write(new File(outputDir, "top10_cpa.csv"), ranked);
    }

    private List<CampaignStats> selectTop(Collection<CampaignStats> stats,
                                           Comparator<CampaignStats> heapOrder,
                                           Comparator<CampaignStats> outputOrder) {
        PriorityQueue<CampaignStats> pq = new PriorityQueue<>(TOP_N + 1, heapOrder);
        for (CampaignStats s : stats) {
            pq.offer(s);
            if (pq.size() > TOP_N) pq.poll();
        }
        List<CampaignStats> result = new ArrayList<>(pq);
        result.sort(outputOrder);
        return result;
    }

    private void write(File outFile, List<CampaignStats> rows) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outFile.toPath(), StandardCharsets.UTF_8))) {
            pw.println(HEADER);
            for (CampaignStats s : rows) {
                double ctr = MetricsCalculator.computeCtr(s.totalImpressions, s.totalClicks);
                double cpa = MetricsCalculator.computeCpa(s.totalSpend, s.totalConversions);
                pw.println(formatRow(s, ctr, cpa));
            }
        }
    }

    private String formatRow(CampaignStats s, double ctr, double cpa) {
        String cpaStr = Double.isNaN(cpa) ? "" : String.format(Locale.ROOT, "%.6f", cpa);
        return String.format(Locale.ROOT, "%s,%d,%d,%.2f,%d,%.6f,%s",
            s.campaignId, s.totalImpressions, s.totalClicks, s.totalSpend,
            s.totalConversions, ctr, cpaStr);
    }
}
