package com.ricky.drivebay.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Close(String channel, String problem) implements Message {
    public Close {
        if (channel == null || channel.isBlank()) throw new IllegalArgumentException("channel mancante");
    }

    public Close(String channel) {
        this(channel, null);
    }
}