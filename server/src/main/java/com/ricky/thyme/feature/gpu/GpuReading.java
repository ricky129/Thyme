package com.ricky.thyme.feature.gpu;

import java.util.List;

public record GpuReading(List<GpuSnapshot> devices, List<String> errors) {
    public GpuReading {
        devices = List.copyOf(devices);
        errors = List.copyOf(errors);
    }
}
