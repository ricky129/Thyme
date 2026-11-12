package com.ricky.thyme.feature.memory;

public record MemorySnapshot(
        long totalBytes,
        long usedBytes,
        long swapTotalBytes,
        long swapUsedBytes) {
    public MemorySnapshot {
        if (usedBytes < 0 || usedBytes > totalBytes) {
            throw new IllegalArgumentException("valori RAM non validi");
        }
        if (swapUsedBytes < 0 || swapUsedBytes > swapTotalBytes) {
            throw new IllegalArgumentException("valori swap non validi");
        }
    }
}
