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
    void selectsSinglePrimaryFilesystemWithoutHardcodingRoot() {
        DashboardSnapshot rootSecond = new DashboardSnapshot(Instant.EPOCH, null, null, null,
                new DiskSnapshot(List.of(), List.of(
                        filesystem("/home", 500), filesystem("/", 100), filesystem("/media/usb", 32))),
                List.of(), Map.of());

        List<OverviewCard> rootCards = OverviewCard.cards(rootSecond);
        assertEquals(OverviewCard.SYSTEM_CARD_COUNT + 1, rootCards.size());
        assertEquals("/", rootCards.get(OverviewCard.SYSTEM_CARD_COUNT).mountPoint());
        assertTrue(rootCards.get(OverviewCard.SYSTEM_CARD_COUNT).filesystem());
        assertEquals(OverviewCard.Kind.GPU, rootCards.get(3).kind());

        DashboardSnapshot withoutRoot = new DashboardSnapshot(Instant.EPOCH, null, null, null,
                new DiskSnapshot(List.of(), List.of(
                        filesystem("/mnt/backup/a", 800),
                        filesystem("/var", 50),
                        filesystem("/data", 500))),
                List.of(), Map.of());

        List<OverviewCard> withoutRootCards = OverviewCard.cards(withoutRoot);
        assertEquals(OverviewCard.SYSTEM_CARD_COUNT + 1, withoutRootCards.size());
        assertEquals("/data", withoutRootCards.get(OverviewCard.SYSTEM_CARD_COUNT).mountPoint());
        assertEquals(2, OverviewCard.primaryFilesystemIndex(withoutRoot.disk().filesystems()));
    }

    @Test
    void rejectsFilesystemCardsWithoutMountPoint() {
        assertThrows(IllegalArgumentException.class,
                () -> new OverviewCard(OverviewCard.Kind.FILESYSTEM, " "));
    }

    private static DiskSnapshot.Filesystem filesystem(String mountPoint, long totalBytes) {
        return new DiskSnapshot.Filesystem("/dev/sda1", mountPoint, "ext4",
                totalBytes, totalBytes / 2, totalBytes / 2, List.of("sda1"),
                0.0, 0.0, 0.0, 0.0, 0.0);
    }
}
