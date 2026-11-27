package com.ricky.thyme.feature.network;

import java.io.IOException;
import java.nio.file.Path;

/** Stub: collection logic is intentionally not implemented yet. */
public final class LinuxNetworkProvider implements INetworkProvider {

    public LinuxNetworkProvider() {
        // TODO: delegate to the default host root
    }

    public LinuxNetworkProvider(Path hostRoot) {
        if (hostRoot == null) {
            throw new IllegalArgumentException("host root is required");
        }
        // TODO: resolve /proc/net/dev from hostRoot
    }

    @Override
    public NetworkSnapshot read() throws IOException {
        // TODO: implement network activity collection
        return null;
    }
}
