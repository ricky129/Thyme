package com.ricky.thyme.feature.dashboard;

import com.ricky.thyme.feature.disk.DiskSnapshot;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A selectable tile in the overview KPI collage.
 *
 * <p>The collage has four system tiles ({@code CPU}, {@code RAM}, {@code Network},
 * {@code GPU}) followed by the disk tiles: one tile for zero or one filesystem,
 * two for two filesystems, and two plus a {@code +N more} tile for three or more.</p>
 */
public record OverviewCard(Kind kind, String mountPoint, int overflowCount) {

    /** Card kinds in rendering order. */
    public enum Kind {
        CPU, RAM, NETWORK, GPU, FILESYSTEM, MORE_FILESYSTEMS, DISK_TOTAL
    }

    public static final OverviewCard CPU = new OverviewCard(Kind.CPU, null);
    public static final OverviewCard RAM = new OverviewCard(Kind.RAM, null);
    public static final OverviewCard NETWORK = new OverviewCard(Kind.NETWORK, null);
    public static final OverviewCard GPU = new OverviewCard(Kind.GPU, null);
    public static final OverviewCard DISK_TOTAL =
            new OverviewCard(Kind.DISK_TOTAL, null);

    /** Number of cards that always precede the filesystem cards. */
    public static final int SYSTEM_CARD_COUNT = 4;

    /** Filesystems that get their own tile before the overflow tile. */
    public static final int MAX_VISIBLE_FILESYSTEM_CARDS = 2;

    public OverviewCard(Kind kind, String mountPoint) {
        this(kind, mountPoint, 0);
    }

    public OverviewCard {
        if (kind == null) {
            throw new IllegalArgumentException("card kind is required");
        }
        if (kind == Kind.FILESYSTEM) {
            if (mountPoint == null || mountPoint.isBlank()) {
                throw new IllegalArgumentException("filesystem card requires a mount point");
            }
            overflowCount = 0;
        } else if (kind == Kind.MORE_FILESYSTEMS) {
            if (overflowCount < 1) {
                throw new IllegalArgumentException("overflow card requires at least one filesystem");
            }
            mountPoint = null;
        } else {
            mountPoint = null;
            overflowCount = 0;
        }
    }

    /**
     * Builds the collage card list for the given snapshot.
     *
     * @param snapshot current dashboard data, possibly {@code null}
     * @return immutable card list of five to seven cards
     */
    public static List<OverviewCard> cards(DashboardSnapshot snapshot) {
        List<OverviewCard> cards = new ArrayList<>(SYSTEM_CARD_COUNT + 3);
        cards.add(CPU);
        cards.add(RAM);
        cards.add(NETWORK);
        cards.add(GPU);
        List<DiskSnapshot.Filesystem> ranked = rankedFilesystems(filesystemsOf(snapshot));
        if (ranked.isEmpty()) {
            cards.add(DISK_TOTAL);
        } else {
            int visible = Math.min(MAX_VISIBLE_FILESYSTEM_CARDS, ranked.size());
            for (int index = 0; index < visible; index++) {
                cards.add(new OverviewCard(Kind.FILESYSTEM, ranked.get(index).mountPoint()));
            }
            if (ranked.size() > MAX_VISIBLE_FILESYSTEM_CARDS) {
                cards.add(new OverviewCard(Kind.MORE_FILESYSTEMS, null,
                        ranked.size() - MAX_VISIBLE_FILESYSTEM_CARDS));
            }
        }
        return List.copyOf(cards);
    }

    private static List<DiskSnapshot.Filesystem> filesystemsOf(DashboardSnapshot snapshot) {
        return snapshot == null || snapshot.disk() == null
                ? List.of() : snapshot.disk().filesystems();
    }

    /**
     * Orders filesystems without hardcoding a mount path: shallowest normalized
     * mount depth first, ties break by largest total size, then provider order.
     */
    public static List<DiskSnapshot.Filesystem> rankedFilesystems(
            List<DiskSnapshot.Filesystem> filesystems) {
        if (filesystems == null || filesystems.isEmpty()) {
            return List.of();
        }
        List<Integer> order = new ArrayList<>();
        for (int index = 0; index < filesystems.size(); index++) {
            order.add(index);
        }
        order.sort(Comparator.<Integer>comparingInt(
                index -> mountDepth(filesystems.get(index).mountPoint()))
                .thenComparingLong(index -> -filesystems.get(index).totalBytes())
                .thenComparingInt(index -> index));
        List<DiskSnapshot.Filesystem> ranked = new ArrayList<>(order.size());
        for (int index : order) {
            ranked.add(filesystems.get(index));
        }
        return List.copyOf(ranked);
    }

    /** Ranked filesystems that do not have their own tile. */
    public static List<DiskSnapshot.Filesystem> overflowFilesystems(DashboardSnapshot snapshot) {
        List<DiskSnapshot.Filesystem> ranked = rankedFilesystems(filesystemsOf(snapshot));
        return ranked.size() <= MAX_VISIBLE_FILESYSTEM_CARDS
                ? List.of()
                : List.copyOf(ranked.subList(MAX_VISIBLE_FILESYSTEM_CARDS, ranked.size()));
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
        List<DiskSnapshot.Filesystem> filesystems = filesystemsOf(snapshot);
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

    /** Whether this card opens the drill-in for the filesystems without their own tile. */
    public boolean moreFilesystems() {
        return kind == Kind.MORE_FILESYSTEMS;
    }

    /** Label used on the compact KPI card. */
    public String label() {
        return switch (kind) {
            case CPU -> "CPU";
            case RAM -> "RAM";
            case NETWORK -> "Network";
            case GPU -> "GPU";
            case FILESYSTEM -> mountPoint;
            case MORE_FILESYSTEMS -> "+" + overflowCount + " more";
            case DISK_TOTAL -> "Disk I/O";
        };
    }

    /** Label used in the detail overlay title. */
    public String detailLabel() {
        return kind == Kind.MORE_FILESYSTEMS ? "Other disks" : label();
    }
}
