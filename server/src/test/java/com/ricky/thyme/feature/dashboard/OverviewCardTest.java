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
        assertEquals(OverviewCard.Kind.NETWORK, cards.get(2).kind());
        assertEquals("Network", cards.get(2).label());
        assertEquals(OverviewCard.Kind.DISK_TOTAL, cards.getLast().kind());
        assertEquals("Disk I/O", cards.getLast().label());
    }

    @Test
    void oneFilesystemGivesFiveCards() {
        List<OverviewCard> cards = OverviewCard.cards(snapshot(filesystem("/", 100)));

        assertEquals(5, cards.size());
        assertEquals("/", cards.get(4).mountPoint());
        assertTrue(cards.get(4).filesystem());
    }

    @Test
    void twoFilesystemsGiveSixCardsInRankedOrder() {
        List<OverviewCard> cards = OverviewCard.cards(snapshot(
                filesystem("/home", 500), filesystem("/", 100)));

        assertEquals(6, cards.size());
        assertEquals("/", cards.get(4).mountPoint());
        assertEquals("/home", cards.get(5).mountPoint());
    }

    @Test
    void threeOrMoreFilesystemsAddAnOverflowCard() {
        DashboardSnapshot three = snapshot(filesystem("/home", 500), filesystem("/", 100),
                filesystem("/media/usb", 32));

        List<OverviewCard> cards = OverviewCard.cards(three);
        assertEquals(7, cards.size());
        assertEquals("/", cards.get(4).mountPoint());
        assertEquals("/home", cards.get(5).mountPoint());
        assertTrue(cards.get(6).moreFilesystems());
        assertEquals(1, cards.get(6).overflowCount());
        assertEquals("+1 more", cards.get(6).label());
        assertEquals("Other disks", cards.get(6).detailLabel());
        assertEquals(List.of("/media/usb"), OverviewCard.overflowFilesystems(three).stream()
                .map(DiskSnapshot.Filesystem::mountPoint).toList());

        DashboardSnapshot four = snapshot(filesystem("/a/b", 5), filesystem("/", 100),
                filesystem("/data", 500), filesystem("/mnt/x/y", 9));
        List<OverviewCard> fourCards = OverviewCard.cards(four);
        assertEquals(7, fourCards.size());
        assertEquals(2, fourCards.get(6).overflowCount());
        assertEquals(List.of("/a/b", "/mnt/x/y"), OverviewCard.overflowFilesystems(four).stream()
                .map(DiskSnapshot.Filesystem::mountPoint).toList());
    }

    @Test
    void selectsPrimaryFilesystemWithoutHardcodingRoot() {
        DashboardSnapshot withoutRoot = snapshot(
                filesystem("/mnt/backup/a", 800),
                filesystem("/var", 50),
                filesystem("/data", 500));

        assertEquals("/data", OverviewCard.cards(withoutRoot).get(4).mountPoint());
        assertEquals(2, OverviewCard.primaryFilesystemIndex(withoutRoot.disk().filesystems()));
    }

    @Test
    void rejectsInvalidCards() {
        assertThrows(IllegalArgumentException.class,
                () -> new OverviewCard(OverviewCard.Kind.FILESYSTEM, " "));
        assertThrows(IllegalArgumentException.class,
                () -> new OverviewCard(OverviewCard.Kind.MORE_FILESYSTEMS, null, 0));
    }

    private static DashboardSnapshot snapshot(DiskSnapshot.Filesystem... filesystems) {
        return new DashboardSnapshot(Instant.EPOCH, null, null, null,
                new DiskSnapshot(List.of(), List.of(filesystems)), List.of(), Map.of());
    }

    private static DiskSnapshot.Filesystem filesystem(String mountPoint, long totalBytes) {
        return new DiskSnapshot.Filesystem("/dev/sda1", mountPoint, "ext4",
                totalBytes, totalBytes / 2, totalBytes / 2, List.of("sda1"),
                0.0, 0.0, 0.0, 0.0, 0.0);
    }
}
