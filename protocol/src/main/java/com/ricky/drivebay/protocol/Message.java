package com.ricky.drivebay.protocol;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = Open.class,  name = "open"),
        @JsonSubTypes.Type(value = Data.class,  name = "data"),
        @JsonSubTypes.Type(value = Close.class, name = "close")
})
public sealed interface Message permits Open, Data, Close {
    String channel();
}