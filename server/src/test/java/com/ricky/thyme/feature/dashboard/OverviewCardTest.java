package com.ricky.thyme.feature.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ricky.thyme.feature.disk.DiskSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OverviewCardTest {
    @Test
    void fallsBackToDiskTotalWithoutFilesystems() {
        List<OverviewCard> cards = OverviewCard.cards(null);

        assertEquals(OverviewCard.SYSTEM_CARD_COUNT + 1, cards.size());
        assertEquals(OverviewCard.Kind.DISK_TOTAL, cards.getLast().kind());
        assertEquals("Disk I/O", cards.getLast().label());
    }

    @Test
    void appendsOneCardPerFilesystemInProviderOrder() {
        DashboardSnapshot snapshot = new DashboardSnapshot(Instant.EPOCH, null, null, null,
                new DiskSnapshot(List.of(), List.of(
                        filesystem("/"), filesystem("/home"), filesystem("/media/usb"))),
                List.of(), Map.of());

        List<OverviewCard> cards = OverviewCard.cards(snapshot);

        assertEquals(OverviewCard.SYSTEM_CARD_COUNT + 3, cards.size());
        assertEquals("/", cards.get(OverviewCard.SYSTEM_CARD_COUNT).mountPoint());
        assertEquals("/home", cards.get(OverviewCard.SYSTEM_CARD_COUNT + 1).mountPoint());
        assertEquals("/media/usb", cards.get(OverviewCard.SYSTEM_CARD_COUNT + 2).mountPoint());
        assertTrue(cards.get(OverviewCard.SYSTEM_CARD_COUNT).filesystem());
        assertEquals(OverviewCard.Kind.GPU, cards.get(3).kind());
    }

    @Test
    void rejectsFilesystemCardsWithoutMountPoint() {
        assertThrows(IllegalArgumentException.class,
                () -> new OverviewCard(OverviewCard.Kind.FILESYSTEM, " "));
    }

    private static DiskSnapshot.Filesystem filesystem(String mountPoint) {
        return new DiskSnapshot.Filesystem("/dev/sda1", mountPoint, "ext4",
                100, 60, 40, List.of("sda1"), 0.0, 0.0, 0.0, 0.0, 0.0);
    }
}
