package com.ricky.thyme.feature.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.input.MouseAction;
import com.googlecode.lanterna.input.MouseActionType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.virtual.DefaultVirtualTerminal;
import com.ricky.thyme.exec.ICommandRunner;
import com.ricky.thyme.feature.disk.DiskSnapshot;
import com.ricky.thyme.feature.gpu.GpuReading;
import com.ricky.thyme.feature.gpu.GpuSnapshot;
import com.ricky.thyme.feature.network.NetworkSnapshot;
import com.ricky.thyme.feature.services.ServiceProvider;
import com.ricky.thyme.feature.telemetry.EventLogBuffer;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class DashboardPageTest {
    @Test
    void directionalKeysMoveAcrossCollageInTwoDimensionsWithoutChangingPage() {
        DashboardPage page = new DashboardPage(null, null, null, null, null, null);

        page.handleKey(new KeyStroke(KeyType.ArrowRight));
        assertEquals(1, page.selectedCard());
        page.handleKey(new KeyStroke(KeyType.ArrowDown));
        assertEquals(3, page.selectedCard());
        page.handleKey(new KeyStroke(KeyType.ArrowDown));
        assertEquals(4, page.selectedCard());
        page.handleKey(new KeyStroke(KeyType.ArrowUp));
        assertEquals(3, page.selectedCard());
        page.handleKey(new KeyStroke(KeyType.ArrowLeft));
        assertEquals(2, page.selectedCard());
        page.handleKey(new KeyStroke(KeyType.ArrowUp));
        assertEquals(0, page.selectedCard());

        page.handleKey(new KeyStroke('j', false, false));
        assertEquals(2, page.selectedCard());
        page.handleKey(new KeyStroke('j', false, false));
        assertEquals(4, page.selectedCard());
        page.handleKey(new KeyStroke('k', false, false));
        assertEquals(2, page.selectedCard());
        page.handleKey(new KeyStroke('l', false, false));
        assertEquals(3, page.selectedCard());
        page.handleKey(new KeyStroke('h', false, false));
        assertEquals(2, page.selectedCard());
        page.handleKey(new KeyStroke('k', false, false));
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
    void filesystemDrillInIsScopedToItsOwnMount() {
        DashboardPage page = new DashboardPage(null, null, null, null, null, null);
        page.updateSnapshot(null, null, null, new DiskSnapshot(List.of(), List.of(
                filesystem("/home"), filesystem("/"), filesystem("/media/usb"))), null, Map.of());

        page.handleKey(new KeyStroke(KeyType.ArrowDown));
        page.handleKey(new KeyStroke(KeyType.ArrowDown));
        assertEquals(4, page.selectedCard());

        page.handleKey(new KeyStroke(KeyType.Enter));
        assertTrue(page.showingDetails());
        assertEquals(0, page.selectedFilesystem());

        // Tab toggles read/write inside the disk drill-in.
        assertFalse(page.filesystemWriteSelected());
        page.handleKey(new KeyStroke(KeyType.Tab));
        assertTrue(page.filesystemWriteSelected());
        page.handleKey(new KeyStroke(KeyType.Tab));
        assertFalse(page.filesystemWriteSelected());

        // ↑/↓ do not switch filesystems in a single-mount drill-in.
        page.handleKey(new KeyStroke(KeyType.ArrowDown));
        assertEquals(0, page.selectedFilesystem());

        // ←/→ move to the previous/next drill-in.
        page.handleKey(new KeyStroke(KeyType.ArrowLeft));
        assertEquals(3, page.selectedCard());
        page.handleKey(new KeyStroke(KeyType.ArrowRight));
        page.handleKey(new KeyStroke(KeyType.ArrowRight));
        assertEquals(5, page.selectedCard());

        page.handleKey(new KeyStroke(KeyType.Escape));
        assertFalse(page.showingDetails());
        assertEquals(5, page.selectedCard());
    }

    @Test
    void moreFilesystemsDrillInCyclesTheOverflowMounts() {
        DashboardPage page = new DashboardPage(null, null, null, null, null, null);
        page.updateSnapshot(null, null, null, new DiskSnapshot(List.of(), List.of(
                filesystem("/"), filesystem("/home"), filesystem("/media/usb"),
                filesystem("/mnt/a"))), null, Map.of());

        for (int index = 0; index < 6; index++) {
            page.handleKey(new KeyStroke(KeyType.ArrowRight));
        }
        assertEquals(6, page.selectedCard());
        page.handleKey(new KeyStroke(KeyType.Enter));
        assertTrue(page.showingDetails());
        assertEquals(0, page.selectedFilesystem());

        page.handleKey(new KeyStroke(KeyType.ArrowDown));
        assertEquals(1, page.selectedFilesystem());
        page.handleKey(new KeyStroke(KeyType.ArrowDown));
        assertEquals(1, page.selectedFilesystem());
        page.handleKey(new KeyStroke('k', false, false));
        assertEquals(0, page.selectedFilesystem());

        page.handleKey(new KeyStroke(KeyType.Tab));
        assertTrue(page.filesystemWriteSelected());
    }

    @Test
    void navigatesTheSixAndSevenTileCollages() {
        DashboardPage seven = new DashboardPage(null, null, null, null, null, null);
        seven.updateSnapshot(null, null, null, diskWithFilesystems(), null, Map.of());
        seven.handleKey(new KeyStroke(KeyType.ArrowDown));
        seven.handleKey(new KeyStroke(KeyType.ArrowDown));
        assertEquals(4, seven.selectedCard());
        seven.handleKey(new KeyStroke(KeyType.ArrowRight));
        seven.handleKey(new KeyStroke(KeyType.ArrowRight));
        assertEquals(6, seven.selectedCard());
        seven.handleKey(new KeyStroke(KeyType.ArrowUp));
        assertEquals(3, seven.selectedCard());
        seven.handleKey(new KeyStroke(KeyType.ArrowDown));
        assertEquals(5, seven.selectedCard());
        seven.handleKey(new KeyStroke(KeyType.ArrowUp));
        seven.handleKey(new KeyStroke(KeyType.ArrowLeft));
        assertEquals(2, seven.selectedCard());
        seven.handleKey(new KeyStroke(KeyType.ArrowDown));
        assertEquals(4, seven.selectedCard());
        seven.handleKey(new KeyStroke(KeyType.ArrowUp));
        assertEquals(2, seven.selectedCard());

        DashboardPage six = new DashboardPage(null, null, null, null, null, null);
        six.updateSnapshot(null, null, null, new DiskSnapshot(List.of(), List.of(
                filesystem("/"), filesystem("/home"))), null, Map.of());
        six.handleKey(new KeyStroke(KeyType.ArrowRight));
        six.handleKey(new KeyStroke(KeyType.ArrowDown));
        six.handleKey(new KeyStroke(KeyType.ArrowDown));
        assertEquals(5, six.selectedCard());
        six.handleKey(new KeyStroke(KeyType.ArrowUp));
        assertEquals(3, six.selectedCard());
    }

    @Test
    void rendersNetworkRatesFromTheSnapshot() throws IOException {
        DashboardPage page = new DashboardPage(null, null, null, null, null, null);
        page.updateSnapshot(null, null, null, null, null, Map.of(), null,
                new NetworkSnapshot(1024.0 * 1024, 512.0 * 1024, List.of()));
        TerminalSize size = new TerminalSize(120, 35);
        DefaultVirtualTerminal terminal = new DefaultVirtualTerminal(size);
        try (Screen screen = new TerminalScreen(terminal)) {
            screen.startScreen();
            page.render(screen.newTextGraphics(), size);
            screen.refresh();
            StringBuilder text = new StringBuilder();
            for (int row = 0; row < size.getRows(); row++) {
                for (int column = 0; column < size.getColumns(); column++) {
                    text.append(terminal.getCharacter(column, row).getCharacter());
                }
            }
            assertTrue(text.toString().contains("↓1"));
            assertTrue(text.toString().contains("↑0"));
        }
    }

    @Test
    void clampsCardSelectionToSevenCollageTilesWithManyFilesystems() {
        DashboardPage page = new DashboardPage(null, null, null, null, null, null);
        page.updateSnapshot(null, null, null, diskWithFilesystems(), null, Map.of());

        for (int index = 0; index < 20; index++) {
            page.handleKey(new KeyStroke(KeyType.ArrowRight));
        }

        assertEquals(6, page.selectedCard());
    }

    @Test
    void mouseClickOpensTileDrillInAndClickOutsideOrOnBorderCloses() throws IOException {
        DashboardPage page = new DashboardPage(null, null, null, null, null, null);
        page.updateSnapshot(null, null, null, diskWithFilesystems(), null, Map.of());
        TerminalSize size = new TerminalSize(120, 35);
        DefaultVirtualTerminal terminal = new DefaultVirtualTerminal(size);
        try (Screen screen = new TerminalScreen(terminal)) {
            screen.startScreen();
            page.render(screen.newTextGraphics(), size);
        }

        for (DashboardOverviewRenderer.TileRect tile : DashboardOverviewRenderer.computeTiles(size, 7)) {
            page.handleKey(new MouseAction(MouseActionType.CLICK_DOWN, 1,
                    new TerminalPosition(tile.x() + 2, tile.y() + 2)));
            assertTrue(page.showingDetails());
            assertEquals(tile.cardIndex(), page.selectedCard());

            DashboardOverviewRenderer.OverlayRect overlay =
                    DashboardOverviewRenderer.computeOverlayRect(size);
            page.handleKey(new MouseAction(MouseActionType.CLICK_DOWN, 1,
                    new TerminalPosition(overlay.x() + 2, overlay.y())));
            assertFalse(page.showingDetails());
        }

        // Clicking a tile, then clicking outside the overlay closes it.
        DashboardOverviewRenderer.TileRect first = DashboardOverviewRenderer.computeTiles(size, 7).get(0);
        page.handleKey(new MouseAction(MouseActionType.CLICK_DOWN, 1,
                new TerminalPosition(first.x() + 2, first.y() + 2)));
        assertTrue(page.showingDetails());
        page.handleKey(new MouseAction(MouseActionType.CLICK_DOWN, 1, new TerminalPosition(0, 0)));
        assertFalse(page.showingDetails());

        // Clicking in the Failed events sidebar, right-click, or release does not open a drill-in.
        page.handleKey(new MouseAction(MouseActionType.CLICK_DOWN, 1, new TerminalPosition(90, 10)));
        page.handleKey(new MouseAction(MouseActionType.CLICK_DOWN, 3,
                new TerminalPosition(first.x() + 2, first.y() + 2)));
        page.handleKey(new MouseAction(MouseActionType.CLICK_RELEASE, 1,
                new TerminalPosition(first.x() + 2, first.y() + 2)));
        assertFalse(page.showingDetails());
    }

    @Test
    void filtersEventsInteractively() {
        DashboardPage page = new DashboardPage(null, null, null, null, null, null);
        assertFalse(page.capturesGlobalNavigation());

        // Press '/' to start filtering events
        page.handleKey(new KeyStroke('/', false, false));
        assertTrue(page.capturesGlobalNavigation());

        // Type 'service'
        page.handleKey(new KeyStroke('s', false, false));
        page.handleKey(new KeyStroke('e', false, false));

        // Enter confirms query and exits capture
        page.handleKey(new KeyStroke(KeyType.Enter));
        assertFalse(page.capturesGlobalNavigation());

        // Esc clears the query
        page.handleKey(new KeyStroke(KeyType.Escape));
        assertFalse(page.capturesGlobalNavigation());
    }

    @Test
    void sampleServicesLogsOnlyFailuresAndRecoveries() {
        AtomicReference<ICommandRunner.Result> nextResult = new AtomicReference<>(new ICommandRunner.Result(
                0,
                """
                [
                  {"unit":"nginx.service","load":"loaded","active":"active","sub":"running","description":"Web"},
                  {"unit":"alsa-state.service","load":"loaded","active":"inactive","sub":"dead","description":"Audio"}
                ]
                """,
                ""));
        DashboardPage page = new DashboardPage(null, null, null, null,
                new ServiceProvider((command, timeout) -> nextResult.get()), null);

        page.sampleServices();
        assertEquals(0, page.eventLog().size());

        nextResult.set(new ICommandRunner.Result(
                0,
                """
                [
                  {"unit":"nginx.service","load":"loaded","active":"failed","sub":"failed","description":"Web"},
                  {"unit":"alsa-state.service","load":"loaded","active":"inactive","sub":"dead","description":"Audio"}
                ]
                """,
                ""));
        page.sampleServices();
        assertEquals(1, page.eventLog().size());
        assertEquals(EventLogBuffer.Severity.CRITICAL, page.eventLog().snapshot().get(0).severity());
        assertEquals("Service nginx.service failed", page.eventLog().snapshot().get(0).message());

        nextResult.set(new ICommandRunner.Result(
                0,
                """
                [{"unit":"nginx.service","load":"loaded","active":"active","sub":"running","description":"Web"}]
                """,
                ""));
        page.sampleServices();
        assertEquals(2, page.eventLog().size());
        assertEquals(EventLogBuffer.Severity.GOOD, page.eventLog().snapshot().get(1).severity());
        assertEquals("Service nginx.service recovered", page.eventLog().snapshot().get(1).message());

        nextResult.set(new ICommandRunner.Result(
                0,
                """
                [{"unit":"foo.service","load":"loaded","active":"failed","sub":"failed","description":"Foo"}]
                """,
                ""));
        page.sampleServices();
        assertEquals(3, page.eventLog().size());
        assertEquals(EventLogBuffer.Severity.CRITICAL, page.eventLog().snapshot().get(2).severity());
        assertEquals("Service foo.service failed", page.eventLog().snapshot().get(2).message());

        nextResult.set(new ICommandRunner.Result(0, "[]", ""));
        page.sampleServices();
        assertEquals(4, page.eventLog().size());
        assertEquals(EventLogBuffer.Severity.GOOD, page.eventLog().snapshot().get(3).severity());
        assertEquals("Service foo.service recovered", page.eventLog().snapshot().get(3).message());
    }

    @Test
    void sampleServicesLogsProviderErrorAndRecovery() {
        AtomicReference<ICommandRunner.Result> nextResult = new AtomicReference<>(
                new ICommandRunner.Result(1, "", "systemctl failed"));
        DashboardPage page = new DashboardPage(null, null, null, null,
                new ServiceProvider((command, timeout) -> nextResult.get()), null);

        page.sampleServices();
        assertEquals(1, page.eventLog().size());
        assertEquals(EventLogBuffer.Severity.CRITICAL, page.eventLog().snapshot().get(0).severity());
        assertEquals("Services provider error: systemctl: systemctl failed",
                page.eventLog().snapshot().get(0).message());

        nextResult.set(new ICommandRunner.Result(0, "[]", ""));
        page.sampleServices();
        assertEquals(2, page.eventLog().size());
        assertEquals(EventLogBuffer.Severity.GOOD, page.eventLog().snapshot().get(1).severity());
        assertEquals("Services provider recovered", page.eventLog().snapshot().get(1).message());
    }

    @Test
    void thresholdEventsStaySilentWhenHealthyAndRecoverWhenDroppingBelowThreshold() {
        AtomicReference<Double> gpuTemp = new AtomicReference<>(65.0);
        DashboardPage page = new DashboardPage(
                null,
                null,
                () -> new GpuReading(
                        List.of(new GpuSnapshot("NVIDIA", "RTX 4090", "0000:01:00.0",
                                10.0, null, null, gpuTemp.get(), null, null, null, null)),
                        List.of()),
                null,
                null,
                null);

        page.sampleGpu();
        page.filesystemEvent(filesystem("/", 50.0));
        assertEquals(0, page.eventLog().size());

        gpuTemp.set(82.0);
        page.sampleGpu();
        gpuTemp.set(92.0);
        page.sampleGpu();
        gpuTemp.set(70.0);
        page.sampleGpu();

        page.filesystemEvent(filesystem("/", 86.0));
        page.filesystemEvent(filesystem("/", 96.0));
        page.filesystemEvent(filesystem("/", 60.0));

        List<EventLogBuffer.Event> events = page.eventLog().snapshot();
        assertEquals(6, events.size());
        assertEquals(EventLogBuffer.Severity.WARM, events.get(0).severity());
        assertEquals("GPU temperature above threshold (warning)", events.get(0).message());
        assertEquals(EventLogBuffer.Severity.CRITICAL, events.get(1).severity());
        assertEquals("GPU temperature above threshold (critical)", events.get(1).message());
        assertEquals(EventLogBuffer.Severity.GOOD, events.get(2).severity());
        assertEquals("GPU temperature back to normal", events.get(2).message());
        assertEquals(EventLogBuffer.Severity.WARM, events.get(3).severity());
        assertEquals("Filesystem / above threshold (warning)", events.get(3).message());
        assertEquals(EventLogBuffer.Severity.CRITICAL, events.get(4).severity());
        assertEquals("Filesystem / above threshold (critical)", events.get(4).message());
        assertEquals(EventLogBuffer.Severity.GOOD, events.get(5).severity());
        assertEquals("Filesystem / back to normal", events.get(5).message());
    }

    private static DiskSnapshot diskWithFilesystems() {
        return new DiskSnapshot(List.of(), List.of(
                filesystem("/"), filesystem("/home"), filesystem("/media/usb")));
    }

    private static DiskSnapshot.Filesystem filesystem(String mountPoint) {
        return filesystem(mountPoint, 0.0);
    }

    private static DiskSnapshot.Filesystem filesystem(String mountPoint, Double utilizationPercent) {
        return new DiskSnapshot.Filesystem("/dev/sda1", mountPoint, "ext4",
                100, 60, 40, List.of("sda1"), 0.0, 0.0, utilizationPercent, 0.0, 0.0);
    }
}
