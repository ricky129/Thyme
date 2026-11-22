package com.ricky.thyme.feature.host;

/** Supplies the immutable host identity shown in the header. */
public interface HostProvider {
    HostSnapshot read();
}
