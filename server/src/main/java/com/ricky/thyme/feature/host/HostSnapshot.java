package com.ricky.thyme.feature.host;

/**
 * Minimal host identity for the header: hostname and uptime.
 *
 * <p>Values come from OSHI on the collection thread; the renderer only reads
 * this immutable snapshot.</p>
 */
public record HostSnapshot(String hostname, long uptimeSeconds) {
    public HostSnapshot {
        if (hostname == null || hostname.isBlank()) {
            hostname = "host";
        }
        if (uptimeSeconds < 0) {
            uptimeSeconds = 0;
        }
    }
}
