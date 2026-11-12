package com.ricky.thyme.feature.gpu;

import com.ricky.thyme.exec.CommandRunner;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class NvidiaGpuProvider {
    private static final Duration TIMEOUT = Duration.ofSeconds(3);
    private static final List<String> QUERY = List.of(
            "nvidia-smi",
            "--query-gpu=name,pci.bus_id,utilization.gpu,memory.total,memory.used,temperature.gpu,"
                    + "power.draw,power.limit,clocks.gr,clocks.mem",
            "--format=csv,noheader,nounits");

    private final CommandRunner runner;

    public NvidiaGpuProvider(CommandRunner runner) {
        this.runner = runner;
    }

    public Map<String, GpuSnapshot> readAll() throws IOException, InterruptedException {
        CommandRunner.Result result = runner.run(QUERY, TIMEOUT);
        if (!result.ok()) {
            throw new IOException("nvidia-smi: " + result.stderr().strip());
        }
        Map<String, GpuSnapshot> devices = new HashMap<>();
        for (String line : result.stdout().lines().toList()) {
            if (line.isBlank()) {
                continue;
            }
            List<String> fields = parseCsvLine(line);
            if (fields.size() != 10) {
                throw new IOException("riga nvidia-smi non valida: attesi 10 campi");
            }
            String pciAddress = normalizePciAddress(fields.get(1));
            if (pciAddress == null) {
                throw new IOException("indirizzo PCI non valido nell'output nvidia-smi");
            }
            GpuSnapshot snapshot = new GpuSnapshot(
                    "NVIDIA",
                    fields.get(0),
                    pciAddress,
                    decimal(fields.get(2)),
                    mebibytes(fields.get(3)),
                    mebibytes(fields.get(4)),
                    decimal(fields.get(5)),
                    decimal(fields.get(6)),
                    decimal(fields.get(7)),
                    integer(fields.get(8)),
                    integer(fields.get(9)));
            devices.put(pciAddress, snapshot);
        }
        return Map.copyOf(devices);
    }

    static List<String> parseCsvLine(String line) throws IOException {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char character = line.charAt(i);
            if (character == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (character == ',' && !quoted) {
                fields.add(field.toString().strip());
                field.setLength(0);
            } else {
                field.append(character);
            }
        }
        if (quoted) {
            throw new IOException("campo CSV non terminato");
        }
        fields.add(field.toString().strip());
        return fields;
    }

    static String normalizePciAddress(String value) {
        String[] domainAndBus = value.trim().toLowerCase(Locale.ROOT).split(":", 2);
        if (domainAndBus.length != 2) {
            return null;
        }
        String[] busAndDevice = domainAndBus[1].split(":", 2);
        if (busAndDevice.length != 2 || !busAndDevice[0].matches("[0-9a-f]{2}")
                || !busAndDevice[1].matches("[0-9a-f]{2}\\.[0-7]")) {
            return null;
        }
        return busAndDevice[0] + ":" + busAndDevice[1];
    }

    private static Double decimal(String value) {
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.equalsIgnoreCase("[n/a]") || normalized.equalsIgnoreCase("n/a")) {
            return null;
        }
        try {
            double parsed = Double.parseDouble(normalized);
            return Double.isFinite(parsed) && parsed >= 0 ? parsed : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long integer(String value) {
        Double parsed = decimal(value);
        return parsed == null ? null : parsed.longValue();
    }

    private static Long mebibytes(String value) {
        Double parsed = decimal(value);
        if (parsed == null || parsed > Long.MAX_VALUE / (1024.0 * 1024)) {
            return null;
        }
        return Math.round(parsed * 1024 * 1024);
    }
}
