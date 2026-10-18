package com.ricky.drivebay.agent.feature.memory;

import com.ricky.drivebay.protocol.feature.memory.MemorySnapshot;

public interface MemoryProvider {
    MemorySnapshot read();
}