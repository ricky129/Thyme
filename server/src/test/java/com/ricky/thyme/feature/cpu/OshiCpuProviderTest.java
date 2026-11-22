package com.ricky.thyme.feature.cpu;

import org.junit.jupiter.api.Test;
import oshi.util.GlobalConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OshiCpuProviderTest {

    @Test
    void loadsOshiDefaultLinuxCpuSensorPriorityWithoutAcpitz() {
        String hwmonNames = GlobalConfig.get("oshi.os.linux.sensors.hwmon.names");
        assertNotNull(hwmonNames);
        assertTrue(hwmonNames.contains("coretemp"));
        assertTrue(hwmonNames.contains("k10temp"));
        assertFalse(hwmonNames.contains("acpitz"));
        assertEquals("cpu-thermal,x86_pkg_temp",
                GlobalConfig.get("oshi.os.linux.sensors.cputemperature.types"));
    }

    @Test
    void readsSnapshotFromOshi() {
        CpuSnapshot snapshot = new OshiCpuProvider().read();
        assertNotNull(snapshot.model());
        assertTrue(snapshot.logicalProcessors() >= 1);
        assertEquals(snapshot.logicalProcessors(), snapshot.cores().size());
    }
}
