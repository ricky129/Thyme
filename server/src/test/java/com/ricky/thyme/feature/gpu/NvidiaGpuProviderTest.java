package com.ricky.thyme.feature.gpu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NvidiaGpuProviderTest {
    @TempDir
    Path tempDir;

    @Test
    void readsNvmlMetricsAndModelFromProcDriverInformation() throws Exception {
        Path proc = tempDir.resolve("proc/driver/nvidia/gpus");
        Path gpuDir = proc.resolve("0000:65:00.0");
        Files.createDirectories(gpuDir);
        Files.writeString(gpuDir.resolve("information"), """
                Model:\t\tNVIDIA RTX 4090
                IRQ:\t\t142
                Bus Location:\t0000:65:00.0
                """);

        StubNvml nvml = new StubNvml(true, Map.of("0000:65:00.0", "nvml-0"));
        var gpu = new NvidiaGpuProvider(nvml, proc, tempDir.resolve("sys/bus/pci/devices"))
                .readAll(List.of())
                .get("65:00.0");

        assertEquals("NVIDIA", gpu.vendor());
        assertEquals("NVIDIA RTX 4090", gpu.name());
        assertEquals("65:00.0", gpu.pciAddress());
        assertEquals(42.0, gpu.utilizationPercent());
        assertEquals(8L * 1024 * 1024 * 1024, gpu.memoryTotalBytes());
        assertEquals(1024L * 1024 * 1024, gpu.memoryUsedBytes());
        assertEquals(55.0, gpu.temperatureCelsius());
        assertEquals(120.5, gpu.powerWatts());
        assertNull(gpu.powerLimitWatts());
        assertEquals(1800L, gpu.graphicsClockMhz());
        assertEquals(7000L, gpu.memoryClockMhz());
    }

    @Test
    void treatsNegativeNvmlSentinelsAsUnavailable() throws Exception {
        Path proc = tempDir.resolve("proc/driver/nvidia/gpus");
        Files.createDirectories(proc.resolve("0000:01:00.0"));

        StubNvml nvml = new StubNvml(true, Map.of("01:00.0", "nvml-0"),
                -1.0, -1L, -1L, -1.0, -1.0, -1L, -1L);
        var gpu = new NvidiaGpuProvider(nvml, proc, tempDir.resolve("sys/bus/pci/devices"))
                .readAll(List.of())
                .get("01:00.0");

        assertEquals("NVIDIA GPU (01:00.0)", gpu.name());
        assertNull(gpu.utilizationPercent());
        assertNull(gpu.memoryTotalBytes());
        assertNull(gpu.memoryUsedBytes());
        assertNull(gpu.temperatureCelsius());
        assertNull(gpu.powerWatts());
        assertNull(gpu.powerLimitWatts());
        assertNull(gpu.graphicsClockMhz());
        assertNull(gpu.memoryClockMhz());
    }

    @Test
    void discoversDisplayDevicesFromSysBusPciAndIgnoresAudioFunction() throws Exception {
        Path pciDevices = tempDir.resolve("sys/bus/pci/devices");
        Path gpuDevice = pciDevices.resolve("0000:02:00.0");
        Path audioDevice = pciDevices.resolve("0000:02:00.1");
        Files.createDirectories(gpuDevice);
        Files.createDirectories(audioDevice);
        Files.writeString(gpuDevice.resolve("vendor"), "0x10de\n");
        Files.writeString(gpuDevice.resolve("class"), "0x030000\n");
        Files.writeString(gpuDevice.resolve("product_name"), "NVIDIA L4\n");
        Files.writeString(audioDevice.resolve("vendor"), "0x10de\n");
        Files.writeString(audioDevice.resolve("class"), "0x040300\n");

        StubNvml nvml = new StubNvml(true, Map.of("0000:02:00.0", "nvml-l4"));
        var devices = new NvidiaGpuProvider(nvml, tempDir.resolve("proc/driver/nvidia/gpus"), pciDevices)
                .readAll(List.of());

        assertEquals(1, devices.size());
        assertTrue(devices.containsKey("02:00.0"));
        assertEquals("NVIDIA L4", devices.get("02:00.0").name());
    }

    @Test
    void failsWhenNvmlUnavailableOrCandidatesDoNotMatchNvml() throws Exception {
        Path proc = tempDir.resolve("proc/driver/nvidia/gpus");
        Files.createDirectories(proc.resolve("0000:01:00.0"));

        NvidiaGpuProvider unavailable = new NvidiaGpuProvider(
                new StubNvml(false, Map.of()), proc, tempDir.resolve("sys/bus/pci/devices"));
        assertThrows(IOException.class, () -> unavailable.readAll(List.of()));

        NvidiaGpuProvider unmatched = new NvidiaGpuProvider(
                new StubNvml(true, Map.of()), proc, tempDir.resolve("sys/bus/pci/devices"));
        assertThrows(IOException.class, () -> unmatched.readAll(List.of()));
    }

    private record StubNvml(
            boolean available,
            Map<String, String> ids,
            double utilization,
            long vramTotal,
            long vramUsed,
            double temperature,
            double powerDraw,
            long coreClock,
            long memoryClock) implements NvidiaGpuProvider.NvmlClient {
        StubNvml(boolean available, Map<String, String> ids) {
            this(available, ids, 42.0, 8L * 1024 * 1024 * 1024, 1024L * 1024 * 1024,
                    55.0, 120.5, 1800L, 7000L);
        }

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public String findDevice(String pciBusId) {
            return ids.get(pciBusId);
        }

        @Override
        public double getGpuUtilization(String deviceId) {
            return utilization;
        }

        @Override
        public long getVramTotal(String deviceId) {
            return vramTotal;
        }

        @Override
        public long getVramUsed(String deviceId) {
            return vramUsed;
        }

        @Override
        public double getTemperature(String deviceId) {
            return temperature;
        }

        @Override
        public double getPowerDraw(String deviceId) {
            return powerDraw;
        }

        @Override
        public long getCoreClockMhz(String deviceId) {
            return coreClock;
        }

        @Override
        public long getMemoryClockMhz(String deviceId) {
            return memoryClock;
        }
    }
}
