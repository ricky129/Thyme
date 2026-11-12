package com.ricky.thyme.feature.memory;

import oshi.SystemInfo;

public final class OshiMemoryProvider {
    private final SystemInfo systemInfo = new SystemInfo();

    public MemorySnapshot read() {
        var memory = systemInfo.getHardware().getMemory();
        var virtualMemory = memory.getVirtualMemory();
        long total = memory.getTotal();
        long available = memory.getAvailable();
        long swapTotal = virtualMemory.getSwapTotal();
        long swapUsed = virtualMemory.getSwapUsed();
        return new MemorySnapshot(total, total - available, swapTotal, swapUsed);
    }
}
