package com.ricky.thyme.feature.gpu;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class SysfsGpuProvider {
    private static final Pattern PCI_ADDRESS = Pattern.compile(
            "(?:[0-9a-fA-F]{4}:)?([0-9a-fA-F]{2}:[0-9a-fA-F]{2}\\.[0-7])");
    private final Path drmRoot;

    public SysfsGpuProvider() {
        this(Path.of("/sys/class/drm"));
    }

    SysfsGpuProvider(Path drmRoot) {
        this.drmRoot = drmRoot;
    }

    public List<GpuSnapshot> read(List<Path> cards) throws IOException {
        List<GpuSnapshot> devices = new ArrayList<>();
        for (Path card : cards) {
            String vendorId = readText(card.resolve("device/vendor"));
            String vendor = switch (vendorId.toLowerCase(Locale.ROOT)) {
                case "0x1002" -> "AMD";
                case "0x8086" -> "Intel";
                default -> null;
            };
            if (vendor == null)
                continue;
            Path device = card.resolve("device");
            String pciAddress = pciAddress(device);
            String name = firstText(device.resolve("product_name"), device.resolve("product"));
            if (name == null)
                name = vendor + " GPU (" + card.getFileName() + ")";
            devices.add(new GpuSnapshot(
                    vendor,
                    name,
                    pciAddress,
                    decimal(readText(device.resolve("gpu_busy_percent"))),
                    nonNegativeLong(readText(device.resolve("mem_info_vram_total"))),
                    nonNegativeLong(readText(device.resolve("mem_info_vram_used"))),
                    temperature(device.resolve("hwmon")),
                    power(device.resolve("hwmon")),
                    null,
                    graphicsClock(device),
                    memoryClock(device)));
        }
        return List.copyOf(devices);
    }

    List<Path> findCards() throws IOException {
        if (!Files.isDirectory(drmRoot))
            return List.of();
        try (Stream<Path> children = Files.list(drmRoot)) {
            return children
                    .filter(path -> path.getFileName().toString().matches("card[0-9]+"))
                    .filter(path -> Files.isReadable(path.resolve("device/vendor")))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
    }

    static String pciAddress(Path device) throws IOException {
        Path realPath = device.toRealPath();
        Matcher matcher = PCI_ADDRESS.matcher(realPath.getFileName().toString());
        if (!matcher.matches())
            return realPath.getFileName().toString().toLowerCase(Locale.ROOT);
        return matcher.group(1).toLowerCase(Locale.ROOT);
    }

    private static Double temperature(Path hwmonRoot) throws IOException {
        if (!Files.isDirectory(hwmonRoot))
            return null;
        try (Stream<Path> hwmon = Files.list(hwmonRoot)) {
            double maximum = Double.NEGATIVE_INFINITY;
            for (Path sensor : hwmon.toList()) {
                try (Stream<Path> files = Files.list(sensor)) {
                    for (Path input : files.filter(file -> file.getFileName().toString()
                                    .matches("temp[0-9]+_input"))
                            .toList()) {
                        Double milliCelsius = decimal(readText(input));
                        if (milliCelsius != null && milliCelsius <= 200_000)
                            maximum = Math.max(maximum, milliCelsius / 1000);
                    }
                }
            }
            return Double.isFinite(maximum) ? maximum : null;
        }
    }

    private static Double power(Path hwmonRoot) throws IOException {
        if (!Files.isDirectory(hwmonRoot))
            return null;
        try (Stream<Path> hwmon = Files.list(hwmonRoot)) {
            for (Path sensor : hwmon.toList()) {
                for (String filename : List.of("power1_average", "power1_input")) {
                    Double microwatts = decimal(readText(sensor.resolve(filename)));
                    if (microwatts != null)
                        return microwatts / 1_000_000;
                }
            }
        }
        return null;
    }

    private static Long graphicsClock(Path device) throws IOException {
        Long direct = nonNegativeLong(readText(device.resolve("gt/gt0/rps_cur_freq_mhz")));
        if (direct == null)
            direct = nonNegativeLong(readText(device.resolve("rps_cur_freq_mhz")));
        if (direct != null)
            return direct;
        return currentDpmClock(device.resolve("pp_dpm_sclk"));
    }

    private static Long memoryClock(Path device) throws IOException {
        return currentDpmClock(device.resolve("pp_dpm_mclk"));
    }

    private static Long currentDpmClock(Path path) throws IOException {
        String text = readText(path);
        if (text == null)
            return null;
        for (String line : text.lines().toList()) {
            if (line.contains("*")) {
                Matcher matcher = Pattern.compile("([0-9]+)\\s*Mhz", Pattern.CASE_INSENSITIVE).matcher(line);
                if (matcher.find())
                    return Long.parseLong(matcher.group(1));
            }
        }
        return null;
    }

    private static String firstText(Path... paths) throws IOException {
        for (Path path : paths) {
            String text = readText(path);
            if (text != null && !text.isBlank())
                return text;
        }
        return null;
    }

    private static String readText(Path path) throws IOException {
        if (!Files.isRegularFile(path) || !Files.isReadable(path))
            return null;
        return Files.readString(path).strip();
    }

    private static Long nonNegativeLong(String text) throws IOException {
        if (text == null)
            return null;
        try {
            long value = Long.parseLong(text);
            return value >= 0 ? value : null;
        } catch (NumberFormatException e) {
            throw new IOException("non-numeric sysfs value: " + text, e);
        }
    }

    private static Double decimal(String text) throws IOException {
        if (text == null)
            return null;
        try {
            double value = Double.parseDouble(text);
            return Double.isFinite(value) && value >= 0 ? value : null;
        } catch (NumberFormatException e) {
            throw new IOException("non-numeric sysfs value: " + text, e);
        }
    }
}
