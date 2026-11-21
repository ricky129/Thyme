package com.ricky.thyme.feature.dashboard;

import com.ricky.thyme.feature.disk.DiskSnapshot;
import com.ricky.thyme.feature.telemetry.TimeSeriesBuffer;
import java.util.List;

/**
 * Render view of one mounted filesystem and its I/O histories.
 *
 * <p>Rates are stored in bytes per second, as reported by the provider; the
 * dashboard converts them to MiB/s when drawing.</p>
 */
public record FilesystemSeries(
        String mountPoint,
        String type,
        long usedBytes,
        long totalBytes,
        Double readBytesPerSecond,
        Double writeBytesPerSecond,
        Double utilizationPercent,
        List<String> backingDevices,
        TimeSeriesBuffer readHistory,
        TimeSeriesBuffer writeHistory) {

    public FilesystemSeries {
        if (mountPoint == null || mountPoint.isBlank() || type == null) {
            throw new IllegalArgumentException("filesystem mount point and type are required");
        }
        backingDevices = List.copyOf(backingDevices);
        readHistory = readHistory == null ? new TimeSeriesBuffer() : readHistory;
        writeHistory = writeHistory == null ? new TimeSeriesBuffer() : writeHistory;
    }

    /** Builds a render view from a provider snapshot and its histories. */
    public static FilesystemSeries from(DiskSnapshot.Filesystem filesystem,
            TimeSeriesBuffer readHistory, TimeSeriesBuffer writeHistory) {
        return new FilesystemSeries(
                filesystem.mountPoint(),
                filesystem.type(),
                filesystem.usedBytes(),
                filesystem.totalBytes(),
                filesystem.readBytesPerSecond(),
                filesystem.writeBytesPerSecond(),
                filesystem.utilizationPercent(),
                filesystem.backingDevices(),
                readHistory,
                writeHistory);
    }
}
