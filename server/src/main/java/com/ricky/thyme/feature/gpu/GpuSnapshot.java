package com.ricky.thyme.feature.gpu;

public record GpuSnapshot(
        String vendor,
        String name,
        String pciAddress,
        Double utilizationPercent,
        Long memoryTotalBytes,
        Long memoryUsedBytes,
        Double temperatureCelsius,
        Double powerWatts,
        Double powerLimitWatts,
        Long graphicsClockMhz,
        Long memoryClockMhz) {
    public GpuSnapshot {
        if (vendor == null || vendor.isBlank() || name == null || pciAddress == null)
            throw new IllegalArgumentException("invalid GPU identity");
        if (utilizationPercent != null
                && (!Double.isFinite(utilizationPercent) || utilizationPercent < 0 || utilizationPercent > 100))
            throw new IllegalArgumentException("GPU usage is out of range");
        if (memoryTotalBytes != null && memoryTotalBytes < 0
                || memoryUsedBytes != null && memoryUsedBytes < 0
                || memoryTotalBytes != null && memoryUsedBytes != null && memoryUsedBytes > memoryTotalBytes)
            throw new IllegalArgumentException("invalid GPU memory");
        validateNonNegative(temperatureCelsius, "GPU temperature");
        validateNonNegative(powerWatts, "GPU power");
        validateNonNegative(powerLimitWatts, "GPU power limit");
        if (graphicsClockMhz != null && graphicsClockMhz < 0
                || memoryClockMhz != null && memoryClockMhz < 0)
            throw new IllegalArgumentException("invalid GPU frequency");
    }

    private static void validateNonNegative(Double value, String field) {
        if (value != null && (!Double.isFinite(value) || value < 0))
            throw new IllegalArgumentException("invalid " + field);
    }
}
