package com.ricky.thyme.feature.cpu;

import java.util.List;

public record CpuSnapshot(
        String model,
        String architecture,
        int logicalProcessors,
        Double totalUsagePercent,
        List<Core> cores,
        Double loadAverage1,
        Double loadAverage5,
        Double loadAverage15) {
    public CpuSnapshot {
        if (model == null || architecture == null || logicalProcessors < 1) {
            throw new IllegalArgumentException("identità CPU non valida");
        }
        validatePercent(totalUsagePercent);
        validateLoadAverage(loadAverage1);
        validateLoadAverage(loadAverage5);
        validateLoadAverage(loadAverage15);
        cores = List.copyOf(cores);
        if (cores.size() != logicalProcessors) {
            throw new IllegalArgumentException("il numero di core non corrisponde ai processori logici");
        }
    }

    private static void validatePercent(Double value) {
        if (value != null && (!Double.isFinite(value) || value < 0 || value > 100)) {
            throw new IllegalArgumentException("utilizzo CPU fuori intervallo");
        }
    }

    private static void validateLoadAverage(Double value) {
        if (value != null && (!Double.isFinite(value) || value < 0)) {
            throw new IllegalArgumentException("load average non valido");
        }
    }

    public record Core(int index, Double usagePercent, Long frequencyHz) {
        public Core {
            if (index < 0) {
                throw new IllegalArgumentException("indice core non valido");
            }
            validatePercent(usagePercent);
            if (frequencyHz != null && frequencyHz < 0) {
                throw new IllegalArgumentException("frequenza CPU non valida");
            }
        }
    }
}
