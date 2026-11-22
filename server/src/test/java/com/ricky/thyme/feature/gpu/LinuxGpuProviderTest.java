package com.ricky.thyme.feature.gpu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LinuxGpuProviderTest {
    @TempDir
    Path tempDir;

    @Test
    void discoversNvidiaGpuFromLoadedDriverWithoutDrmCard() throws Exception {
        Path drm = tempDir.resolve("sys/class/drm");
        Path nvidiaProc = tempDir.resolve("proc/driver/nvidia/gpus");
        Path gpuProc = nvidiaProc.resolve("0000:01:00.0");
        Files.createDirectories(drm);
        Files.createDirectories(gpuProc);
        Files.writeString(gpuProc.resolve("information"), "Model:\tNVIDIA GeForce GTX 1050\n");

        NvidiaGpuProvider nvidiaProvider = new NvidiaGpuProvider(
                nvmlClient(true, "0000:01:00.0"),
                nvidiaProc,
                tempDir.resolve("sys/bus/pci/devices"));

        GpuReading reading = new LinuxGpuProvider(new SysfsGpuProvider(drm), nvidiaProvider, nvidiaProc).read();

        assertEquals(1, reading.devices().size());
        assertEquals("NVIDIA GeForce GTX 1050", reading.devices().getFirst().name());
        assertEquals("01:00.0", reading.devices().getFirst().pciAddress());
        assertTrue(reading.errors().isEmpty());
    }

    @Test
    void discoversNvidiaGpuFromDrmCardWhenProcIsAbsent() throws Exception {
        Path drm = tempDir.resolve("sys/class/drm");
        Path pciDevice = tempDir.resolve("sys/devices/pci0000:00/0000:03:00.0");
        Path card = drm.resolve("card0");
        Files.createDirectories(pciDevice);
        Files.createDirectories(card);
        Files.createSymbolicLink(card.resolve("device"), pciDevice);
        Files.writeString(pciDevice.resolve("vendor"), "0x10de\n");
        Files.writeString(pciDevice.resolve("product_name"), "NVIDIA GeForce RTX 3080\n");

        Path nvidiaProc = tempDir.resolve("proc/driver/nvidia/gpus");
        NvidiaGpuProvider nvidiaProvider = new NvidiaGpuProvider(
                nvmlClient(true, "0000:03:00.0"),
                nvidiaProc,
                tempDir.resolve("sys/bus/pci/devices"));

        GpuReading reading = new LinuxGpuProvider(new SysfsGpuProvider(drm), nvidiaProvider, nvidiaProc).read();

        assertEquals(1, reading.devices().size());
        assertEquals("NVIDIA GeForce RTX 3080", reading.devices().getFirst().name());
        assertEquals("03:00.0", reading.devices().getFirst().pciAddress());
        assertTrue(reading.errors().isEmpty());
    }

    @Test
    void recordsErrorWhenDriverIsLoadedButNvmlIsUnavailable() throws Exception {
        Path drm = tempDir.resolve("sys/class/drm");
        Path nvidiaProc = tempDir.resolve("proc/driver/nvidia/gpus");
        Files.createDirectories(drm);
        Files.createDirectories(nvidiaProc.resolve("0000:01:00.0"));

        NvidiaGpuProvider nvidiaProvider = new NvidiaGpuProvider(
                nvmlClient(false, "0000:01:00.0"),
                nvidiaProc,
                tempDir.resolve("sys/bus/pci/devices"));

        GpuReading reading = new LinuxGpuProvider(new SysfsGpuProvider(drm), nvidiaProvider, nvidiaProc).read();

        assertTrue(reading.devices().isEmpty());
        assertEquals(1, reading.errors().size());
        assertTrue(reading.errors().getFirst().contains("NVML"));
    }

    private static NvidiaGpuProvider.NvmlClient nvmlClient(boolean available, String expectedBusId) {
        return new NvidiaGpuProvider.NvmlClient() {
            @Override
            public boolean isAvailable() {
                return available;
            }

            @Override
            public String findDevice(String pciBusId) {
                return expectedBusId.equals(pciBusId) ? "nvml-0" : null;
            }

            @Override
            public double getGpuUtilization(String deviceId) {
                return 5.0;
            }

            @Override
            public long getVramTotal(String deviceId) {
                return 4L * 1024 * 1024 * 1024;
            }

            @Override
            public long getVramUsed(String deviceId) {
                return 512L * 1024 * 1024;
            }

            @Override
            public double getTemperature(String deviceId) {
                return 42.0;
            }

            @Override
            public double getPowerDraw(String deviceId) {
                return 35.0;
            }

            @Override
            public long getCoreClockMhz(String deviceId) {
                return 1354L;
            }

            @Override
            public long getMemoryClockMhz(String deviceId) {
                return 3504L;
            }
        };
    }
}
