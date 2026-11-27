package com.ricky.thyme.feature.gpu;

public interface IGpuProvider {
    GpuReading read() throws InterruptedException;
}
