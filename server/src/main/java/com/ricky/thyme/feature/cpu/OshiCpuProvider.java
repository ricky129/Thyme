package com.ricky.thyme.feature.cpu;

import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.Sensors;
import java.util.ArrayList;
import java.util.List;

public final class OshiCpuProvider {
    private final SystemInfo systemInfo = new SystemInfo();
    private final CentralProcessor processor = systemInfo.getHardware().getProcessor();
    private final Sensors sensors = systemInfo.getHardware().getSensors();
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

        previousSystemTicks = systemTicks;
        previousCoreTicks = coreTicks;

        double cpuTemperature = sensors.getCpuTemperature();
        return new CpuSnapshot(
                processor.getProcessorIdentifier().getName(),
                System.getProperty("os.arch", "unknown"),
                processor.getLogicalProcessorCount(),
                totalUsage,
                Double.isFinite(cpuTemperature) && cpuTemperature > 0 ? cpuTemperature : null,
                cores);
    }

    private static Double percent(double fraction) {
        return Double.isFinite(fraction) && fraction >= 0 ? Math.min(100, fraction * 100) : null;
    }

}
