package com.ricky.thyme.feature.dashboard;

import com.ricky.thyme.feature.disk.DiskSnapshot;
import java.util.ArrayList;
import java.util.List;

/**
 * A selectable tile in the overview KPI row.
 *
 * <p>The row is not fixed: the four system cards are always present, then one
 * card per user-visible filesystem follows.  When no filesystem is available a
 * single fallback {@code Disk I/O} card keeps the row complete.</p>
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
     * Builds the card row for the given snapshot.
     *
     * @param snapshot current dashboard data, possibly {@code null}
     * @return immutable card list, never empty
     */
    public static List<OverviewCard> cards(DashboardSnapshot snapshot) {
        List<OverviewCard> cards = new ArrayList<>(SYSTEM_CARD_COUNT + 1);
        cards.add(CPU);
        cards.add(RAM);
        cards.add(TEMPERATURE);
        cards.add(GPU);
        List<DiskSnapshot.Filesystem> filesystems = snapshot == null || snapshot.disk() == null
                ? List.of() : snapshot.disk().filesystems();
        if (filesystems.isEmpty()) {
            cards.add(DISK_TOTAL);
        } else {
            for (DiskSnapshot.Filesystem filesystem : filesystems) {
                cards.add(new OverviewCard(Kind.FILESYSTEM, filesystem.mountPoint()));
            }
        }
        return List.copyOf(cards);
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
