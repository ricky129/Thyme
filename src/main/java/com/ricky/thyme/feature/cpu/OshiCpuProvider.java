package com.ricky.thyme.feature.cpu;

import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import java.util.ArrayList;
import java.util.List;

public final class OshiCpuProvider {
    private final CentralProcessor processor = new SystemInfo().getHardware().getProcessor();
    private long[] previousSystemTicks;
    private long[][] previousCoreTicks;

    public synchronized CpuSnapshot read() {
        long[] systemTicks = processor.getSystemCpuLoadTicks();
        long[][] coreTicks = processor.getProcessorCpuLoadTicks();
        boolean sampleReady = previousSystemTicks != null && previousCoreTicks != null;

        Double totalUsage = sampleReady
                ? percent(processor.getSystemCpuLoadBetweenTicks(previousSystemTicks))
                : null;
        double[] coreLoads = sampleReady
                ? processor.getProcessorCpuLoadBetweenTicks(previousCoreTicks)
                : new double[processor.getLogicalProcessorCount()];
        long[] frequencies = processor.getCurrentFreq();
        List<CpuSnapshot.Core> cores = new ArrayList<>(processor.getLogicalProcessorCount());
        for (int i = 0; i < processor.getLogicalProcessorCount(); i++) {
            Double usage = sampleReady && i < coreLoads.length ? percent(coreLoads[i]) : null;
            Long frequency = i < frequencies.length && frequencies[i] > 0 ? frequencies[i] : null;
            cores.add(new CpuSnapshot.Core(i, usage, frequency));
        }

        double[] loadAverages = processor.getSystemLoadAverage(3);
        previousSystemTicks = systemTicks;
        previousCoreTicks = coreTicks;

        return new CpuSnapshot(
                processor.getProcessorIdentifier().getName(),
                System.getProperty("os.arch", "unknown"),
                processor.getLogicalProcessorCount(),
                totalUsage,
                cores,
                loadAverage(loadAverages, 0),
                loadAverage(loadAverages, 1),
                loadAverage(loadAverages, 2));
    }

    private static Double percent(double fraction) {
        return Double.isFinite(fraction) && fraction >= 0 ? Math.min(100, fraction * 100) : null;
    }

    private static Double loadAverage(double[] averages, int index) {
        if (index >= averages.length || !Double.isFinite(averages[index]) || averages[index] < 0) {
            return null;
        }
        return averages[index];
    }
}
