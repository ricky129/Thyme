package com.ricky.thyme.feature.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DashboardPageTest {
    @Test
    void directionalKeysMoveAcrossKpiCardsWithoutChangingPage() {
        DashboardPage page = new DashboardPage(null, null, null, null, null, null);

        page.handleKey(new KeyStroke(KeyType.ArrowRight));
        page.handleKey(new KeyStroke(KeyType.ArrowDown));
        assertEquals(2, page.selectedCard());

        page.handleKey(new KeyStroke(KeyType.ArrowLeft));
        page.handleKey(new KeyStroke(KeyType.ArrowUp));
        assertEquals(0, page.selectedCard());
    }

    @Test
    void enterOpensAndEscapeClosesCardDetails() {
        DashboardPage page = new DashboardPage(null, null, null, null, null, null);

        page.handleKey(new KeyStroke(KeyType.Enter));
        assertEquals(true, page.showingDetails());
        page.handleKey(new KeyStroke(KeyType.Escape));
        assertEquals(false, page.showingDetails());
    }

    @Test
    void detailKeysChangeRangeAndSelectorWithoutLeavingTheOverlay() {
        DashboardPage page = new DashboardPage(null, null, null, null, null, null);
        page.handleKey(new KeyStroke(KeyType.Enter));
        page.handleKey(new KeyStroke(']', false, false));
        assertEquals(com.ricky.thyme.feature.telemetry.TimeSeriesBuffer.Range.ONE_HOUR, page.detailRange());
        page.handleKey(new KeyStroke(KeyType.Tab));
        assertEquals(true, page.perCoreSelected());
        page.handleKey(new KeyStroke(KeyType.Escape));
        assertEquals(false, page.showingDetails());
    }

    @Test
    void cursorStaysHiddenAtNewestUntilMovingLeft() {
        DashboardPage page = new DashboardPage(null, null, null, null, null, null);
        page.handleKey(new KeyStroke(KeyType.Enter));
        page.handleKey(new KeyStroke('l', false, false));
        assertEquals(-1, page.detailCursor());
        page.handleKey(new KeyStroke('h', false, false));
        assertEquals(-1, page.detailCursor());
    }

    @Test
    void arrowsMoveBetweenDrillInsWhileOpen() {
        DashboardPage page = new DashboardPage(null, null, null, null, null, null);
        page.handleKey(new KeyStroke(KeyType.Enter));
        assertEquals(true, page.showingDetails());

        page.handleKey(new KeyStroke(KeyType.ArrowRight));
        assertEquals(1, page.selectedCard());
        page.handleKey(new KeyStroke(KeyType.ArrowLeft));
        assertEquals(0, page.selectedCard());

        // Clamped at the first drill-in.
        page.handleKey(new KeyStroke(KeyType.ArrowLeft));
        assertEquals(0, page.selectedCard());
    }

    @Test
    void detailCapturesTabBeforeRootPageNavigation() {
        DashboardPage page = new DashboardPage(null, null, null, null, null, null);
        assertEquals(false, page.capturesGlobalNavigation());
        page.handleKey(new KeyStroke(KeyType.Enter));
        assertEquals(true, page.capturesGlobalNavigation());
    }

    @Test
    void eachFilesystemCardOpensItsOwnDrillIn() {
        DashboardPage page = new DashboardPage(null, null, null, null, null, null);
        page.updateSnapshot(null, null, null, diskWithFilesystems(), null, Map.of());

        for (int index = 0; index < 5; index++) {
            page.handleKey(new KeyStroke(KeyType.ArrowRight));
        }
        assertEquals(5, page.selectedCard());

        page.handleKey(new KeyStroke(KeyType.Enter));
        assertEquals(true, page.showingDetails());

        // ↑/↓ switch read/write inside the filesystem drill-in.
        assertEquals(false, page.filesystemWriteSelected());
        page.handleKey(new KeyStroke(KeyType.ArrowDown));
        assertEquals(true, page.filesystemWriteSelected());
        page.handleKey(new KeyStroke(KeyType.ArrowUp));
        assertEquals(false, page.filesystemWriteSelected());

        // ←/→ move to the previous/next drill-in.
        page.handleKey(new KeyStroke(KeyType.ArrowRight));
        assertEquals(6, page.selectedCard());
        page.handleKey(new KeyStroke(KeyType.ArrowLeft));
        assertEquals(5, page.selectedCard());

        page.handleKey(new KeyStroke(KeyType.Escape));
        assertEquals(false, page.showingDetails());
        assertEquals(5, page.selectedCard());
    }

    @Test
    void clampsCardSelectionToTheFilesystemCardCount() {
        DashboardPage page = new DashboardPage(null, null, null, null, null, null);
        page.updateSnapshot(null, null, null, diskWithFilesystems(), null, Map.of());

        for (int index = 0; index < 20; index++) {
            page.handleKey(new KeyStroke(KeyType.ArrowRight));
        }

        assertEquals(OverviewCard.SYSTEM_CARD_COUNT + 2, page.selectedCard());
    }

    private static com.ricky.thyme.feature.disk.DiskSnapshot diskWithFilesystems() {
        return new com.ricky.thyme.feature.disk.DiskSnapshot(List.of(), List.of(
                filesystem("/"), filesystem("/home"), filesystem("/media/usb")));
    }

    private static com.ricky.thyme.feature.disk.DiskSnapshot.Filesystem filesystem(String mountPoint) {
        return new com.ricky.thyme.feature.disk.DiskSnapshot.Filesystem("/dev/sda1", mountPoint, "ext4",
                100, 60, 40, List.of("sda1"), 0.0, 0.0, 0.0, 0.0, 0.0);
    }
}
