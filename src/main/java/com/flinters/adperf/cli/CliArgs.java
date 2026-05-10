package com.flinters.adperf.cli;

import com.flinters.adperf.aggregator.Aggregator;
import com.flinters.adperf.aggregator.AggregationResult;
import com.flinters.adperf.util.PeakHeapTracker;
import com.flinters.adperf.writer.CsvWriter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.util.concurrent.Callable;

@Command(
    name = "aggregator",
    mixinStandardHelpOptions = true,
    version = "1.0.0",
    description = "Aggregates ad performance data from a CSV file and outputs top 10 rankings."
)
public class CliArgs implements Callable<Integer> {

    @Option(names = {"--input", "-i"}, required = true, description = "Path to input CSV file")
    private File input;

    @Option(names = {"--output", "-o"}, required = true, description = "Path to output directory")
    private File output;

    @Override
    public Integer call() {
        if (!input.exists())       return error("Input file not found: " + input);
        if (input.isDirectory())   return error("Input path is a directory, not a file: " + input);
        if (!output.exists() && !output.mkdirs()) return error("Cannot create output directory: " + output);
        if (!output.isDirectory()) return error("Output path is not a directory: " + output);
        if (!output.canWrite())    return error("Output directory is not writable: " + output);

        System.out.println("[INFO] Reading: " + input);
        System.out.println("[INFO] Processing...");

        long startMs = System.currentTimeMillis();
        AggregationResult result;
        double peakHeapMiB;
        try (PeakHeapTracker heapTracker = new PeakHeapTracker()) {
            result = new Aggregator().aggregate(input);
            peakHeapMiB = heapTracker.peakMiB();
        } catch (Exception e) {
            return error("Failed to process input: " + e.getMessage());
        }

        if (result.processedRows() == 0) {
            return error("No data rows found in input file.");
        }

        long elapsedMs = System.currentTimeMillis() - startMs;
        System.out.printf("[INFO] Rows processed  : %,d%n", result.processedRows());
        System.out.printf("[INFO] Rows skipped    : %,d%n", result.skippedRows());
        System.out.printf("[INFO] Unique campaigns: %,d%n", result.stats().size());
        System.out.printf("[INFO] Time elapsed    : %.1fs%n", elapsedMs / 1000.0);
        System.out.printf("[INFO] Peak heap used  : %.1f MiB%n", peakHeapMiB);

        try {
            CsvWriter writer = new CsvWriter(output);
            writer.writeTopCtr(result.stats().values());
            writer.writeTopCpa(result.stats().values());
        } catch (Exception e) {
            return error("Failed to write output: " + e.getMessage());
        }

        System.out.println("[INFO] Written: " + new File(output, "top10_ctr.csv"));
        System.out.println("[INFO] Written: " + new File(output, "top10_cpa.csv"));
        System.out.println("[INFO] Done.");
        return 0;
    }

    private int error(String msg) {
        System.err.println("[ERROR] " + msg);
        return 1;
    }
}
