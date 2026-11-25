package com.ricky.thyme.feature.network;

import java.util.List;

/** Network throughput, aggregate and per interface. Rates are bytes per second. */
public record NetworkSnapshot(
        Double rxBytesPerSecond,
        Double txBytesPerSecond,
        List<Interface> interfaces) {
    public NetworkSnapshot {
        interfaces = interfaces == null ? List.of() : List.copyOf(interfaces);
    }

    public record Interface(String name, Double rxBytesPerSecond, Double txBytesPerSecond) {
    }
}
