package com.ricky.thyme.feature.gpu;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class LinuxGpuProvider implements GpuProvider {

    private final SysfsGpuProvider sysfsProvider;
    private final NvidiaGpuProvider nvidiaProvider;
    private final Path nvidiaProcRoot;

    public LinuxGpuProvider() {
        this(Path.of("/sys"));
    }

    public LinuxGpuProvider(Path sysRoot) {
        this(sysRoot, Path.of("/proc/driver/nvidia/gpus"));
    }

    public LinuxGpuProvider(Path sysRoot, Path nvidiaProcRoot) {
        this(
                new SysfsGpuProvider(sysRoot.resolve("class/drm")),
                new NvidiaGpuProvider(nvidiaProcRoot, sysRoot.resolve("bus/pci/devices")),
                nvidiaProcRoot);
    }

    LinuxGpuProvider(SysfsGpuProvider sysfsProvider, NvidiaGpuProvider nvidiaProvider, Path nvidiaProcRoot) {
        this.sysfsProvider = sysfsProvider;
        this.nvidiaProvider = nvidiaProvider;
        this.nvidiaProcRoot = nvidiaProcRoot;
    }

    @Override
    public GpuReading read() {
        List<String> errors = new ArrayList<>();
        List<GpuSnapshot> devices = new ArrayList<>();
        try {
            List<Path> cards = sysfsProvider.findCards();
            List<Path> nvidiaCards = new ArrayList<>();
            for (Path card : cards) {
                try {
                    String vendor = Files.readString(card.resolve("device/vendor")).strip();
                    if ("0x10de".equalsIgnoreCase(vendor))
                        nvidiaCards.add(card);
                } catch (IOException e) {
                    errors.add("GPU " + card.getFileName() + ": " + e.getMessage());
                }
            }

            boolean nvidiaDriverLoaded = Files.isDirectory(nvidiaProcRoot);
            if (!nvidiaCards.isEmpty() || nvidiaDriverLoaded) {
                try {
                    Map<String, GpuSnapshot> nvidiaDevices = nvidiaProvider.readAll(nvidiaCards);
                    devices.addAll(nvidiaDevices.values());
                } catch (IOException e) {
                    errors.add("NVIDIA (driver detected but telemetry unavailable): " + e.getMessage());
                }
            }

            try {
                devices.addAll(sysfsProvider.read(cards));
            } catch (IOException e) {
                errors.add("GPU AMD/Intel: " + e.getMessage());
            }
        } catch (IOException e) {
            errors.add("GPU detection: " + e.getMessage());
        }
        return new GpuReading(devices, errors);
    }
}
