package com.ricky.thyme.feature.gpu;

public interface GpuProvider {
    GpuReading read() throws InterruptedException;
}
