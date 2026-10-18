package com.ricky.drivebay.protocol;

import java.util.Map;

public record Open(String channel, String payload, Map<String, Object> options) implements Message {
    public Open {
        if (channel == null || channel.isBlank()) throw new IllegalArgumentException("channel mancante");
        if (payload == null || payload.isBlank()) throw new IllegalArgumentException("payload mancante");
        options = (options == null) ? Map.of() : Map.copyOf(options);
    }
}