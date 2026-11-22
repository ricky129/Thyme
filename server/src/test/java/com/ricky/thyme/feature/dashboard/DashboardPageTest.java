package com.ricky.thyme.feature.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.ricky.thyme.exec.CommandRunner;
import com.ricky.thyme.feature.disk.DiskSnapshot;
import com.ricky.thyme.feature.gpu.GpuReading;
import com.ricky.thyme.feature.gpu.GpuSnapshot;
import com.ricky.thyme.feature.services.ServiceProvider;
import com.ricky.thyme.feature.telemetry.EventLogBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
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
        AtomicReference<CommandRunner.Result> nextResult = new AtomicReference<>(new CommandRunner.Result(
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

        nextResult.set(new CommandRunner.Result(
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

        nextResult.set(new CommandRunner.Result(
                0,
                """
                [{"unit":"nginx.service","load":"loaded","active":"active","sub":"running","description":"Web"}]
                """,
                ""));
        page.sampleServices();
        assertEquals(2, page.eventLog().size());
        assertEquals(EventLogBuffer.Severity.GOOD, page.eventLog().snapshot().get(1).severity());
        assertEquals("Service nginx.service recovered", page.eventLog().snapshot().get(1).message());

        nextResult.set(new CommandRunner.Result(
                0,
                """
                [{"unit":"foo.service","load":"loaded","active":"failed","sub":"failed","description":"Foo"}]
                """,
                ""));
        page.sampleServices();
        assertEquals(3, page.eventLog().size());
        assertEquals(EventLogBuffer.Severity.CRITICAL, page.eventLog().snapshot().get(2).severity());
        assertEquals("Service foo.service failed", page.eventLog().snapshot().get(2).message());

        nextResult.set(new CommandRunner.Result(0, "[]", ""));
        page.sampleServices();
        assertEquals(4, page.eventLog().size());
        assertEquals(EventLogBuffer.Severity.GOOD, page.eventLog().snapshot().get(3).severity());
        assertEquals("Service foo.service recovered", page.eventLog().snapshot().get(3).message());
    }

    @Test
    void sampleServicesLogsProviderErrorAndRecovery() {
        AtomicReference<CommandRunner.Result> nextResult = new AtomicReference<>(
                new CommandRunner.Result(1, "", "systemctl failed"));
        DashboardPage page = new DashboardPage(null, null, null, null,
                new ServiceProvider((command, timeout) -> nextResult.get()), null);

        page.sampleServices();
        assertEquals(1, page.eventLog().size());
        assertEquals(EventLogBuffer.Severity.CRITICAL, page.eventLog().snapshot().get(0).severity());
        assertEquals("Services provider error: systemctl: systemctl failed",
                page.eventLog().snapshot().get(0).message());

        nextResult.set(new CommandRunner.Result(0, "[]", ""));
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
