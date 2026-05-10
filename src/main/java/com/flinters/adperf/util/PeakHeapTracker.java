package com.flinters.adperf.util;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class PeakHeapTracker implements AutoCloseable {
    private static final long SAMPLE_INTERVAL_MS = 5;
    private static final double BYTES_PER_MIB = 1024.0 * 1024.0;

    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicLong peakBytes = new AtomicLong(currentHeapBytes());
    private final Thread sampler;

    public PeakHeapTracker() {
        sampler = new Thread(this::sampleLoop, "peak-heap-tracker");
        sampler.setDaemon(true);
        sampler.start();
    }

    public double peakMiB() {
        updatePeak();
        return peakBytes.get() / BYTES_PER_MIB;
    }

    @Override
    public void close() {
        running.set(false);
        try {
            sampler.join(SAMPLE_INTERVAL_MS * 2);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        updatePeak();
    }

    private void sampleLoop() {
        while (running.get()) {
            updatePeak();
            try {
                Thread.sleep(SAMPLE_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void updatePeak() {
        peakBytes.accumulateAndGet(currentHeapBytes(), Math::max);
    }

    private static long currentHeapBytes() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }
}
