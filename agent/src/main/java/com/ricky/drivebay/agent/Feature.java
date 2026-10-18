package com.ricky.drivebay.agent;

import com.ricky.drivebay.protocol.Open;

public interface Feature {
    String payload();                       // nome della feature, es. "memory"

    default boolean isAvailable() {         // false se l'host non la supporta
        return true;
    }

    // Eseguita in un virtual thread dedicato. Quando ritorna, il canale viene chiuso.
    void open(Channel channel, Open request) throws Exception;
}