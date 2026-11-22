package com.ricky.thyme.feature.gpu;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import oshi.util.gpu.NvmlUtilJNA;

public final class NvidiaGpuProvider {
    private static final NvmlClient OSHI_NVML = new NvmlClient() {
        @Override
        public boolean isAvailable() {
            return NvmlUtilJNA.isAvailable();
        }

        @Override
        public String findDevice(String pciBusId) {
            return NvmlUtilJNA.findDevice(pciBusId);
        }

        @Override
        public double getGpuUtilization(String deviceId) {
            return NvmlUtilJNA.getGpuUtilization(deviceId);
        }

        @Override
        public long getVramTotal(String deviceId) {
            return NvmlUtilJNA.getVramTotal(deviceId);
        }

        @Override
        public long getVramUsed(String deviceId) {
            return NvmlUtilJNA.getVramUsed(deviceId);
        }

        @Override
        public double getTemperature(String deviceId) {
            return NvmlUtilJNA.getTemperature(deviceId);
        }

        @Override
        public double getPowerDraw(String deviceId) {
            return NvmlUtilJNA.getPowerDraw(deviceId);
        }

        @Override
        public long getCoreClockMhz(String deviceId) {
            return NvmlUtilJNA.getCoreClockMhz(deviceId);
        }

        @Override
        public long getMemoryClockMhz(String deviceId) {
            return NvmlUtilJNA.getMemoryClockMhz(deviceId);
        }
    };

    private final NvmlClient nvml;
    private final Path nvidiaProcRoot;
    private final Path pciDevicesRoot;

    public NvidiaGpuProvider() {
        this(OSHI_NVML, Path.of("/proc/driver/nvidia/gpus"), Path.of("/sys/bus/pci/devices"));
    }

    public NvidiaGpuProvider(Path nvidiaProcRoot, Path pciDevicesRoot) {
        this(OSHI_NVML, nvidiaProcRoot, pciDevicesRoot);
    }

    NvidiaGpuProvider(NvmlClient nvml, Path nvidiaProcRoot, Path pciDevicesRoot) {
        this.nvml = nvml;
        this.nvidiaProcRoot = nvidiaProcRoot;
        this.pciDevicesRoot = pciDevicesRoot;
    }

    public Map<String, GpuSnapshot> readAll(List<Path> drmCards) throws IOException {
        if (!nvml.isAvailable())
            throw new IOException("NVML library not available");
        Map<String, Candidate> candidates = discoverCandidates(drmCards);
        if (candidates.isEmpty())
            return Map.of();

        Map<String, GpuSnapshot> devices = new LinkedHashMap<>();
        for (Candidate candidate : candidates.values()) {
            String deviceId = resolveDeviceId(candidate);
            if (deviceId == null)
                continue;
            Long memoryTotalBytes = nonNegative(nvml.getVramTotal(deviceId));
            Long memoryUsedBytes = nonNegative(nvml.getVramUsed(deviceId));
            if (memoryTotalBytes != null && memoryUsedBytes != null && memoryUsedBytes > memoryTotalBytes)
                memoryUsedBytes = null;
            devices.put(candidate.pciAddress(), new GpuSnapshot(
                    "NVIDIA",
                    candidate.name(),
                    candidate.pciAddress(),
                    validPercentage(nvml.getGpuUtilization(deviceId)),
                    memoryTotalBytes,
                    memoryUsedBytes,
                    nonNegative(nvml.getTemperature(deviceId)),
                    nonNegative(nvml.getPowerDraw(deviceId)),
                    null,
                    nonNegative(nvml.getCoreClockMhz(deviceId)),
                    nonNegative(nvml.getMemoryClockMhz(deviceId))));
        }
        if (devices.isEmpty())
            throw new IOException("no matching NVIDIA GPU found via NVML");
        return Map.copyOf(devices);
    }

    private String resolveDeviceId(Candidate candidate) {
        String deviceId = nvml.findDevice(candidate.busId());
        if (deviceId == null && !candidate.pciAddress().equals(candidate.busId()))
            deviceId = nvml.findDevice(candidate.pciAddress());
        return deviceId;
    }

    private Map<String, Candidate> discoverCandidates(List<Path> drmCards) throws IOException {
        Map<String, Candidate> candidates = new LinkedHashMap<>();
        discoverFromProc(candidates);
        discoverFromDrmCards(drmCards, candidates);
        discoverFromPciDevices(candidates);
        return candidates;
    }

    private void discoverFromProc(Map<String, Candidate> candidates) throws IOException {
        if (!Files.isDirectory(nvidiaProcRoot))
            return;
        try (Stream<Path> entries = Files.list(nvidiaProcRoot)) {
            for (Path entry : entries.sorted(Comparator.comparing(Path::toString)).toList()) {
                if (!Files.isDirectory(entry))
                    continue;
                String dirBusId = entry.getFileName().toString().trim().toLowerCase(Locale.ROOT);
                String model = null;
                String busLocation = null;
                Path info = entry.resolve("information");
                if (Files.isRegularFile(info) && Files.isReadable(info)) {
                    for (String line : Files.readAllLines(info)) {
                        int colon = line.indexOf(':');
                        if (colon < 0)
                            continue;
                        String key = line.substring(0, colon).trim();
                        String value = line.substring(colon + 1).trim();
                        if ("Model".equalsIgnoreCase(key) && !value.isEmpty()) {
                            model = value;
                        } else if ("Bus Location".equalsIgnoreCase(key) && !value.isEmpty()) {
                            busLocation = value.toLowerCase(Locale.ROOT);
                        }
                    }
                }
                String rawBusId = busLocation != null ? busLocation : dirBusId;
                addCandidate(candidates, rawBusId, model, true);
            }
        }
    }

    private void discoverFromDrmCards(List<Path> drmCards, Map<String, Candidate> candidates) {
        for (Path card : drmCards) {
            Path device = card.resolve("device");
            try {
                Path realDevice = device.toRealPath();
                String rawBusId = realDevice.getFileName().toString().trim().toLowerCase(Locale.ROOT);
                String name = readFirstProduct(device);
                addCandidate(candidates, rawBusId, name, name != null);
            } catch (IOException ignored) {
                // Skip unreadable DRM symlinks and fall back to /proc or /sys/bus/pci/devices.
            }
        }
    }

    private void discoverFromPciDevices(Map<String, Candidate> candidates) throws IOException {
        if (!Files.isDirectory(pciDevicesRoot))
            return;
        try (Stream<Path> entries = Files.list(pciDevicesRoot)) {
            for (Path entry : entries.sorted(Comparator.comparing(Path::toString)).toList()) {
                String vendor = readTrimmed(entry.resolve("vendor"));
                String pciClass = readTrimmed(entry.resolve("class"));
                if (!"0x10de".equalsIgnoreCase(vendor)
                        || pciClass == null
                        || !pciClass.toLowerCase(Locale.ROOT).startsWith("0x03"))
                    continue;
                String rawBusId = entry.getFileName().toString().trim().toLowerCase(Locale.ROOT);
                String name = readFirstProduct(entry);
                addCandidate(candidates, rawBusId, name, name != null);
            }
        }
    }

    private static void addCandidate(
            Map<String, Candidate> candidates, String rawBusId, String name, boolean explicitName) {
        String pciAddress = normalizePciAddress(rawBusId);
        if (pciAddress == null)
            return;
        String resolvedName = name != null && !name.isBlank() ? name : "NVIDIA GPU (" + pciAddress + ")";
        Candidate existing = candidates.get(pciAddress);
        if (existing == null) {
            candidates.put(pciAddress, new Candidate(pciAddress, rawBusId, resolvedName, explicitName));
        } else if (!existing.explicitName() && explicitName) {
            candidates.put(pciAddress, new Candidate(pciAddress, existing.busId(), resolvedName, true));
        }
    }

    private static String readFirstProduct(Path deviceDir) throws IOException {
        for (String filename : List.of("product_name", "product")) {
            String value = readTrimmed(deviceDir.resolve(filename));
            if (value != null && !value.isBlank())
                return value;
        }
        return null;
    }

    private static String readTrimmed(Path path) throws IOException {
        if (!Files.isRegularFile(path) || !Files.isReadable(path))
            return null;
        return Files.readString(path).strip();
    }

    static String normalizePciAddress(String value) {
        if (value == null)
            return null;
        String[] domainAndBus = value.trim().toLowerCase(Locale.ROOT).split(":", 2);
        if (domainAndBus.length != 2 || !domainAndBus[0].matches("[0-9a-f]{4,8}"))
            return null;
        String[] busAndDevice = domainAndBus[1].split(":", 2);
        if (busAndDevice.length != 2 || !busAndDevice[0].matches("[0-9a-f]{2}")
                || !busAndDevice[1].matches("[0-9a-f]{2}\\.[0-7]"))
            return null;
        return busAndDevice[0] + ":" + busAndDevice[1];
    }

    private static Double validPercentage(double value) {
        return Double.isFinite(value) && value >= 0 && value <= 100 ? value : null;
    }

    private static Double nonNegative(double value) {
        return Double.isFinite(value) && value >= 0 ? value : null;
    }

    private static Long nonNegative(long value) {
        return value >= 0 ? value : null;
    }

    private record Candidate(String pciAddress, String busId, String name, boolean explicitName) {
    }

    interface NvmlClient {
        boolean isAvailable();

        String findDevice(String pciBusId);

        double getGpuUtilization(String deviceId);

        long getVramTotal(String deviceId);

        long getVramUsed(String deviceId);

        double getTemperature(String deviceId);

        double getPowerDraw(String deviceId);

        long getCoreClockMhz(String deviceId);

        long getMemoryClockMhz(String deviceId);
    }
}
