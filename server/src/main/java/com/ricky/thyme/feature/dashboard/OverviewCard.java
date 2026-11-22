package com.ricky.thyme.feature.dashboard;

import com.ricky.thyme.feature.disk.DiskSnapshot;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A selectable tile in the overview KPI collage.
 *
 * <p>The collage always contains five tiles: the four system tiles ({@code CPU},
 * {@code RAM}, {@code Temp}, {@code GPU}) followed by a single primary disk tile
 * chosen dynamically from mounted filesystems, or {@code Disk I/O} when no
 * filesystems are available.</p>
 */
public record OverviewCard(Kind kind, String mountPoint) {

    /** Card kinds in rendering order. */
    public enum Kind {
        CPU, RAM, TEMPERATURE, GPU, FILESYSTEM, DISK_TOTAL
    }

    public static final OverviewCard CPU = new OverviewCard(Kind.CPU, null);
    public static final OverviewCard RAM = new OverviewCard(Kind.RAM, null);
    public static final OverviewCard TEMPERATURE =
            new OverviewCard(Kind.TEMPERATURE, null);
    public static final OverviewCard GPU = new OverviewCard(Kind.GPU, null);
    public static final OverviewCard DISK_TOTAL =
            new OverviewCard(Kind.DISK_TOTAL, null);

    /** Number of cards that always precede the filesystem cards. */
    public static final int SYSTEM_CARD_COUNT = 4;

    public OverviewCard {
        if (kind == null) {
            throw new IllegalArgumentException("card kind is required");
        }
        if (kind == Kind.FILESYSTEM) {
            if (mountPoint == null || mountPoint.isBlank()) {
                throw new IllegalArgumentException("filesystem card requires a mount point");
            }
        } else {
            mountPoint = null;
        }
    }

    /**
     * Builds the five-tile collage card list for the given snapshot.
     *
     * @param snapshot current dashboard data, possibly {@code null}
     * @return immutable five-card list
     */
    public static List<OverviewCard> cards(DashboardSnapshot snapshot) {
        List<OverviewCard> cards = new ArrayList<>(SYSTEM_CARD_COUNT + 1);
        cards.add(CPU);
        cards.add(RAM);
        cards.add(TEMPERATURE);
        cards.add(GPU);
        DiskSnapshot.Filesystem primary = primaryFilesystem(snapshot);
        cards.add(primary == null
                ? DISK_TOTAL
                : new OverviewCard(Kind.FILESYSTEM, primary.mountPoint()));
        return List.copyOf(cards);
    }

    /**
     * Selects the primary filesystem index without hardcoding a mount path:
     * shallowest normalized mount depth wins, ties break by largest total size,
     * then provider order.
     */
    public static int primaryFilesystemIndex(List<DiskSnapshot.Filesystem> filesystems) {
        if (filesystems == null || filesystems.isEmpty()) {
            return -1;
        }
        int bestIndex = 0;
        int bestDepth = mountDepth(filesystems.get(0).mountPoint());
        long bestTotalBytes = filesystems.get(0).totalBytes();
        for (int index = 1; index < filesystems.size(); index++) {
            DiskSnapshot.Filesystem candidate = filesystems.get(index);
            int depth = mountDepth(candidate.mountPoint());
            if (depth < bestDepth || depth == bestDepth && candidate.totalBytes() > bestTotalBytes) {
                bestIndex = index;
                bestDepth = depth;
                bestTotalBytes = candidate.totalBytes();
            }
        }
        return bestIndex;
    }

    /** Returns the primary filesystem from {@code snapshot}, or {@code null} if none exist. */
    public static DiskSnapshot.Filesystem primaryFilesystem(DashboardSnapshot snapshot) {
        List<DiskSnapshot.Filesystem> filesystems = snapshot == null || snapshot.disk() == null
                ? List.of() : snapshot.disk().filesystems();
        int index = primaryFilesystemIndex(filesystems);
        return index < 0 ? null : filesystems.get(index);
    }

    private static int mountDepth(String mountPoint) {
        if (mountPoint == null || mountPoint.isBlank()) {
            return Integer.MAX_VALUE;
        }
        try {
            return Path.of(mountPoint).normalize().getNameCount();
        } catch (InvalidPathException ignored) {
            return Integer.MAX_VALUE;
        }
    }

    /** Whether this card represents a mounted filesystem. */
    public boolean filesystem() {
        return kind == Kind.FILESYSTEM;
    }

    /** Label used on the compact KPI card. */
    public String label() {
        return switch (kind) {
            case CPU -> "CPU";
            case RAM -> "RAM";
            case TEMPERATURE -> "Temp";
            case GPU -> "GPU";
            case FILESYSTEM -> mountPoint;
            case DISK_TOTAL -> "Disk I/O";
        };
    }

    /** Label used in the detail overlay title. */
    public String detailLabel() {
        return kind == Kind.TEMPERATURE ? "Temperature" : label();
    }
}
