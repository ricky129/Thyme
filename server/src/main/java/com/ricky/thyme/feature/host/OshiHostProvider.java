package com.ricky.thyme.feature.host;

import oshi.SystemInfo;
import oshi.software.os.OperatingSystem;

/** Reads hostname and uptime from OSHI; no extra command is executed. */
public final class OshiHostProvider implements HostProvider {
    private final SystemInfo systemInfo = new SystemInfo();
    private volatile String hostname;

    @Override
    public HostSnapshot read() {
        OperatingSystem operatingSystem = systemInfo.getOperatingSystem();
        String name = hostname;
        if (name == null) {
            // Resolve the name once: some systems do a DNS lookup here, and the
            // header samples this provider on the existing one-second tick.
            name = operatingSystem.getNetworkParams().getHostName();
            hostname = name;
        }
        return new HostSnapshot(name, operatingSystem.getSystemUptime());
    }
}
