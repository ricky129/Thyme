package com.ricky.thyme.feature.containers;

public record ContainerSnapshot(
        String id,
        String name,
        String image,
        String state,
        String status,
        String runningFor,
        int restartCount,
        Integer exitCode,
        String cpuPercent,
        String memoryUsage,
        String memoryPercent,
        String networkIo,
        String blockIo,
        String processCount) {
    public ContainerSnapshot {
        if (id == null || !id.matches("[0-9a-f]{12,64}")
                || name == null || name.isBlank()
                || image == null || state == null || status == null)
            throw new IllegalArgumentException("invalid container snapshot");
    }
}
