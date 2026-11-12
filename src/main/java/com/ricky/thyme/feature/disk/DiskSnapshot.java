package com.ricky.thyme.feature.disk;

import java.util.List;

public record DiskSnapshot(List<Device> devices, List<Filesystem> filesystems) {
    public DiskSnapshot {
        devices = List.copyOf(devices);
        filesystems = List.copyOf(filesystems);
    }

    public record Device(
            String name,
            Long sizeBytes,
            Double readBytesPerSecond,
            Double writeBytesPerSecond,
            Double readsPerSecond,
            Double writesPerSecond,
            Double utilizationPercent,
            Double averageReadLatencyMillis,
            Double averageWriteLatencyMillis) {
        public Device {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("nome dispositivo disco mancante");
            }
            validateNonNegative(sizeBytes);
            validateNonNegative(readBytesPerSecond);
            validateNonNegative(writeBytesPerSecond);
            validateNonNegative(readsPerSecond);
            validateNonNegative(writesPerSecond);
            validateNonNegative(utilizationPercent);
            validateNonNegative(averageReadLatencyMillis);
            validateNonNegative(averageWriteLatencyMillis);
            if (utilizationPercent != null && utilizationPercent > 100) {
                throw new IllegalArgumentException("utilizzo disco superiore al 100%");
            }
        }
    }

    public record Filesystem(
            String source,
            String mountPoint,
            String type,
            long totalBytes,
            long availableBytes,
            long usedBytes,
            List<String> backingDevices,
            Double readBytesPerSecond,
            Double writeBytesPerSecond,
            Double utilizationPercent,
            Double averageReadLatencyMillis,
            Double averageWriteLatencyMillis) {
        public Filesystem(
                String source, String mountPoint, String type,
                long totalBytes, long availableBytes, long usedBytes) {
            this(source, mountPoint, type, totalBytes, availableBytes, usedBytes,
                    List.of(), null, null, null, null, null);
        }

        public Filesystem {
            if (source == null || mountPoint == null || type == null
                    || totalBytes < 0 || availableBytes < 0 || usedBytes < 0
                    || availableBytes > totalBytes || usedBytes > totalBytes) {
                throw new IllegalArgumentException("dati filesystem non validi");
            }
            backingDevices = List.copyOf(backingDevices);
            validateNonNegative(readBytesPerSecond);
            validateNonNegative(writeBytesPerSecond);
            validateNonNegative(utilizationPercent);
            validateNonNegative(averageReadLatencyMillis);
            validateNonNegative(averageWriteLatencyMillis);
            if (utilizationPercent != null && utilizationPercent > 100) {
                throw new IllegalArgumentException("utilizzo filesystem superiore al 100%");
            }
        }
    }

    private static void validateNonNegative(Number value) {
        if (value == null) {
            return;
        }
        double numericValue = value.doubleValue();
        if (!Double.isFinite(numericValue) || numericValue < 0) {
            throw new IllegalArgumentException("metrica disco negativa o non finita");
        }
    }
}
