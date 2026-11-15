package com.ricky.thyme.feature.gpu;

import com.ricky.thyme.exec.CommandRunner;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;

class NvidiaGpuProviderTest {
    @Test
    void parsesNvidiaMetricsAndMapsPciAddress() throws Exception {
        CommandRunner runner = (command, timeout) -> new CommandRunner.Result(
                0,
                "\"Example, GPU\", 00000000:65:00.0, 42, 8192, 1024, 55, 120.5, 250, 1800, 7000\n",
                "");

        var gpu = new NvidiaGpuProvider(runner).readAll().get("65:00.0");

        assertEquals("Example, GPU", gpu.name());
        assertEquals(42.0, gpu.utilizationPercent());
        assertEquals(8L * 1024 * 1024 * 1024, gpu.memoryTotalBytes());
        assertEquals(55.0, gpu.temperatureCelsius());
        assertEquals(1800L, gpu.graphicsClockMhz());
    }

    @Test
    void treatsUnsupportedNvidiaMetricsAsUnavailable() throws Exception {
        CommandRunner runner = (command, timeout) -> new CommandRunner.Result(
                0,
                "Example, 0000:01:00.0, [N/A], [N/A], [N/A], [N/A], [N/A], [N/A], [N/A], [N/A]\n",
                "");

        var gpu = new NvidiaGpuProvider(runner).readAll().get("01:00.0");

        assertNull(gpu.utilizationPercent());
        assertNull(gpu.memoryUsedBytes());
        assertNull(gpu.powerWatts());
    }

    @Test
    void rejectsMalformedCsvRows() {
        CommandRunner runner = (command, timeout) -> new CommandRunner.Result(0, "malformed\n", "");

        var error = org.junit.jupiter.api.Assertions.assertThrows(
                java.io.IOException.class, () -> new NvidiaGpuProvider(runner).readAll());
        assertEquals(true, error.getMessage().contains("10 fields"));
    }
}
