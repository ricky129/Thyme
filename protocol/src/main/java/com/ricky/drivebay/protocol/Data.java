package com.ricky.drivebay.protocol;

import com.fasterxml.jackson.databind.JsonNode;

public record Data(String channel, JsonNode body) implements Message {
    public Data {
        if (channel == null || channel.isBlank()) throw new IllegalArgumentException("channel mancante");
    }
}