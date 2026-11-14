package com.ricky.thyme.feature.dashboard;

import com.ricky.thyme.feature.cpu.CpuSnapshot;
import com.ricky.thyme.feature.disk.DiskSnapshot;
import com.ricky.thyme.feature.gpu.GpuReading;
import com.ricky.thyme.feature.memory.MemorySnapshot;
import com.ricky.thyme.feature.services.ServiceSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record DashboardSnapshot(
        Instant sampledAt,
        CpuSnapshot cpu,
        MemorySnapshot memory,
        GpuReading gpu,
        DiskSnapshot disk,
        List<ServiceSnapshot> services,
        Map<String, String> errors) {
    public DashboardSnapshot {
        if (sampledAt == null)
            throw new IllegalArgumentException("dashboard timestamp is required");
        services = List.copyOf(services);
        errors = Map.copyOf(errors);
    }
}
