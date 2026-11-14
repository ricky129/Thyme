package com.ricky.thyme.feature.gpu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SysfsGpuProviderTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void readsAmdSysfsMetricsAndLeavesUnsupportedValuesNull() throws Exception {
        Path drm = temporaryDirectory.resolve("drm");
        Path device = temporaryDirectory.resolve("pci/0000:04:00.0");
        Path card = drm.resolve("card0");
        Files.createDirectories(device.resolve("hwmon/hwmon0"));
        Files.createDirectories(drm);
        Files.createDirectories(card);
        Files.createSymbolicLink(card.resolve("device"), device);
        write(device.resolve("vendor"), "0x1002\n");
        write(device.resolve("product_name"), "Example Radeon\n");
        write(device.resolve("gpu_busy_percent"), "63\n");
        write(device.resolve("mem_info_vram_total"), "8589934592\n");
        write(device.resolve("mem_info_vram_used"), "1073741824\n");
        write(device.resolve("hwmon/hwmon0/temp1_input"), "54000\n");
        write(device.resolve("hwmon/hwmon0/power1_average"), "125000000\n");
        write(device.resolve("pp_dpm_sclk"), "0: 500Mhz\n1: 2400Mhz *\n");

        var reading = new SysfsGpuProvider(drm).read(new SysfsGpuProvider(drm).findCards());

        assertEquals(1, reading.size());
        var gpu = reading.getFirst();
        assertEquals("AMD", gpu.vendor());
        assertEquals("04:00.0", gpu.pciAddress());
        assertEquals("Example Radeon", gpu.name());
        assertEquals(63.0, gpu.utilizationPercent());
        assertEquals(54.0, gpu.temperatureCelsius());
        assertEquals(125.0, gpu.powerWatts());
        assertEquals(2400L, gpu.graphicsClockMhz());
        assertNull(gpu.memoryClockMhz());
    }

    private static void write(Path path, String value) throws Exception {
        Files.writeString(path, value);
    }
}
