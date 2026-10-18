package com.ricky.drivebay.protocol.feature.memory;

public record MemorySnapshot(
        long totalBytes,
        long usedBytes,
        long swapTotalBytes,
        long swapUsedBytes) {
}