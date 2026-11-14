package com.ricky.thyme.feature.cpu;

import java.util.List;

public record CpuSnapshot(
        String model,
        String architecture,
        int logicalProcessors,
        Double totalUsagePercent,
        Double temperatureCelsius,
        List<Core> cores,
        Double loadAverage1,
        Double loadAverage5,
        Double loadAverage15) {
    public CpuSnapshot {
        if (model == null || architecture == null || logicalProcessors < 1)
            throw new IllegalArgumentException("invalid CPU identity");
        validatePercent(totalUsagePercent);
        validateTemperature(temperatureCelsius);
        validateLoadAverage(loadAverage1);
        validateLoadAverage(loadAverage5);
        validateLoadAverage(loadAverage15);
        cores = List.copyOf(cores);
        if (cores.size() != logicalProcessors)
            throw new IllegalArgumentException("core count does not match the number of logical processors");
    }

    private static void validatePercent(Double value) {
        if (value != null && (!Double.isFinite(value) || value < 0 || value > 100))
            throw new IllegalArgumentException("CPU usage is out of range");
    }

    private static void validateLoadAverage(Double value) {
        if (value != null && (!Double.isFinite(value) || value < 0))
            throw new IllegalArgumentException("invalid load average");
    }

    private static void validateTemperature(Double value) {
        if (value != null && (!Double.isFinite(value) || value < 0))
            throw new IllegalArgumentException("CPU temperature must be finite and non-negative");
    }

    public record Core(int index, Double usagePercent, Long frequencyHz) {
        public Core {
            if (index < 0)
                throw new IllegalArgumentException("invalid core index");
            validatePercent(usagePercent);
            if (frequencyHz != null && frequencyHz < 0)
                throw new IllegalArgumentException("invalid CPU frequency");
        }
    }
}
