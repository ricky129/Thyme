package com.ricky.drivebay.agent.feature.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ricky.drivebay.agent.Channel;
import com.ricky.drivebay.agent.Feature;
import com.ricky.drivebay.protocol.Open;

public final class MemoryFeature implements Feature {
    private final MemoryProvider provider;
    private final ObjectMapper mapper = new ObjectMapper();

    public MemoryFeature(MemoryProvider provider) {
        this.provider = provider;
    }

    @Override
    public String payload() {
        return "memory";
    }

    @Override
    public void open(Channel channel, Open request) throws InterruptedException {
        // Per ora: manda una fotografia al secondo finché il client non chiude.
        while (!channel.isClosed()) {
            channel.send(mapper.valueToTree(provider.read()));
            Thread.sleep(1000);
        }
    }
}