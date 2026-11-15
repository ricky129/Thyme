package com.ricky.thyme.feature.gpu;

import com.ricky.thyme.exec.CommandRunner;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class LinuxGpuProvider implements GpuProvider {
    private final SysfsGpuProvider sysfsProvider;
    private final NvidiaGpuProvider nvidiaProvider;
    private final Path nvidiaProcRoot;

    public LinuxGpuProvider(CommandRunner runner) {
        this(runner, Path.of("/sys"));
    }

    public LinuxGpuProvider(CommandRunner runner, Path sysRoot) {
        this(runner, sysRoot, Path.of("/proc/driver/nvidia/gpus"));
    }

    public LinuxGpuProvider(CommandRunner runner, Path sysRoot, Path nvidiaProcRoot) {
        this(new SysfsGpuProvider(sysRoot.resolve("class/drm")), new NvidiaGpuProvider(runner), nvidiaProcRoot);
    }

    LinuxGpuProvider(SysfsGpuProvider sysfsProvider, NvidiaGpuProvider nvidiaProvider) {
        this(sysfsProvider, nvidiaProvider, Path.of("/proc/driver/nvidia/gpus"));
    }

    LinuxGpuProvider(SysfsGpuProvider sysfsProvider, NvidiaGpuProvider nvidiaProvider, Path nvidiaProcRoot) {
        this.sysfsProvider = sysfsProvider;
        this.nvidiaProvider = nvidiaProvider;
        this.nvidiaProcRoot = nvidiaProcRoot;
    }

    @Override
    public GpuReading read() throws InterruptedException {
        List<String> errors = new ArrayList<>();
        List<GpuSnapshot> devices = new ArrayList<>();
        try {
            List<Path> cards = sysfsProvider.findCards();
            List<Path> nvidiaCards = new ArrayList<>();
            for (Path card : cards) {
                try {
                    String vendor = java.nio.file.Files.readString(card.resolve("device/vendor")).strip();
                    if ("0x10de".equalsIgnoreCase(vendor))
                        nvidiaCards.add(card);
                } catch (IOException e) {
                    errors.add("GPU " + card.getFileName() + ": " + e.getMessage());
                }
            }

            boolean nvidiaDriverLoaded = java.nio.file.Files.isDirectory(nvidiaProcRoot);
            if (!nvidiaCards.isEmpty() || nvidiaDriverLoaded) {
                try {
                    Map<String, GpuSnapshot> nvidiaDevices = nvidiaProvider.readAll();
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
