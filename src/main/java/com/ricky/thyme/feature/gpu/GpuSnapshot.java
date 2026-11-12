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
        if (vendor == null || vendor.isBlank() || name == null || pciAddress == null) {
            throw new IllegalArgumentException("identità GPU non valida");
        }
        if (utilizationPercent != null
                && (!Double.isFinite(utilizationPercent) || utilizationPercent < 0 || utilizationPercent > 100)) {
            throw new IllegalArgumentException("utilizzo GPU fuori intervallo");
        }
        if (memoryTotalBytes != null && memoryTotalBytes < 0
                || memoryUsedBytes != null && memoryUsedBytes < 0
                || memoryTotalBytes != null && memoryUsedBytes != null && memoryUsedBytes > memoryTotalBytes) {
            throw new IllegalArgumentException("memoria GPU non valida");
        }
        validateNonNegative(temperatureCelsius, "temperatura GPU");
        validateNonNegative(powerWatts, "potenza GPU");
        validateNonNegative(powerLimitWatts, "limite di potenza GPU");
        if (graphicsClockMhz != null && graphicsClockMhz < 0
                || memoryClockMhz != null && memoryClockMhz < 0) {
            throw new IllegalArgumentException("frequenza GPU non valida");
        }
    }

    private static void validateNonNegative(Double value, String field) {
        if (value != null && (!Double.isFinite(value) || value < 0)) {
            throw new IllegalArgumentException(field + " non valida");
        }
    }
}
