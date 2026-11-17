package com.ricky.thyme.feature.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.virtual.DefaultVirtualTerminal;
import com.ricky.thyme.feature.cpu.CpuSnapshot;
import com.ricky.thyme.feature.memory.MemorySnapshot;
import com.ricky.thyme.feature.telemetry.EventLogBuffer;
import com.ricky.thyme.feature.telemetry.TimeSeriesBuffer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Text snapshots for the responsive overview and KPI detail overlay. */
class UiSnapshotTest {
    private static final List<TerminalSize> SIZES = List.of(
            new TerminalSize(48, 15), new TerminalSize(80, 24),
            new TerminalSize(120, 35), new TerminalSize(200, 60));

    @Test
    void writesOverviewAndDetailSnapshotsAtAllSupportedSizes() throws IOException {
        DashboardSnapshot snapshot = snapshot();
        TimeSeriesBuffer cpu = history(42, 900);
        TimeSeriesBuffer memory = history(68, 900);
        TimeSeriesBuffer temperature = history(54, 900);
        TimeSeriesBuffer gpu = history(12, 900);
        TimeSeriesBuffer disk = history(3.5, 900);
        EventLogBuffer events = new EventLogBuffer();
        events.add(new EventLogBuffer.Event(Instant.parse("2026-01-01T12:00:00Z"),
                EventLogBuffer.Severity.WARM, "CPU threshold"));

        for (TerminalSize size : SIZES) {
            writeSnapshot("overview", size, (graphics, terminalSize) ->
                    DashboardOverviewRenderer.draw(graphics, terminalSize, snapshot, cpu, memory,
                            temperature, gpu, disk, events, null, 0, false));
            writeSnapshot("detail", size, (graphics, terminalSize) ->
                    DashboardOverviewRenderer.draw(graphics, terminalSize, snapshot, cpu, memory,
                            temperature, gpu, disk, events, null, 0, true,
                            TimeSeriesBuffer.Range.FIFTEEN_MINUTES, 899, false, true));
        }
        assertTrue(Files.exists(Path.of("target", "ui-snapshots", "overview-120x35.txt")));
    }

    @Test
    void writesPerCoreSnapshotsWithOneGraphPerCore() throws IOException {
        DashboardSnapshot snapshot = snapshotWithCores();
        List<TimeSeriesBuffer> cores = List.of(history(10, 900), history(20, 900),
                history(30, 900), history(40, 900));
        int[] cursors = {899, -1, 120, -1};
        for (TerminalSize size : SIZES) {
            writeSnapshot("per-core", size, (graphics, terminalSize) ->
                    DashboardOverviewRenderer.draw(graphics, terminalSize, snapshot,
                            history(25, 18), history(68, 900), history(5, 900), history(54, 900),
                            history(12, 900), history(3.5, 900), new EventLogBuffer(), null,
                            0, true, TimeSeriesBuffer.Range.ONE_HOUR, 17, true, true,
                            cores, 2, cursors));
        }
        Path snapshotFile = Path.of("target", "ui-snapshots", "per-core-120x35.txt");
        assertTrue(Files.exists(snapshotFile));
        assertEquals(1, Files.readString(snapshotFile).lines()
                .filter(line -> line.contains("history covers 18s of 1h"))
                .count());
    }

    @Test
    void rendersUsedAndSwapSeriesInRamDetail() throws IOException {
        TerminalSize size = new TerminalSize(120, 35);
        DashboardSnapshot snapshot = snapshot();
        writeSnapshot("ram-detail", size, (graphics, terminalSize) ->
                DashboardOverviewRenderer.draw(graphics, terminalSize, snapshot,
                        history(42, 900), history(68, 150), history(12, 150),
                        history(54, 900), history(12, 900), history(3.5, 900),
                        new EventLogBuffer(), null, 1, true,
                        TimeSeriesBuffer.Range.FIFTEEN_MINUTES, 149, false, true,
                        List.of(), 0, new int[0]));
        String text = Files.readString(Path.of("target", "ui-snapshots", "ram-detail-120x35.txt"));
        assertTrue(text.contains("● used"));
        assertTrue(text.contains("○ swap"));
        assertTrue(text.contains("··"), "swap dots should be adjacent column by column");
    }

    @Test
    void hidesSwapDashesWhenSwapUsageIsZero() throws IOException {
        TerminalSize size = new TerminalSize(120, 35);
        DashboardSnapshot snapshot = snapshot();
        writeSnapshot("ram-detail-zero-swap", size, (graphics, terminalSize) ->
                DashboardOverviewRenderer.draw(graphics, terminalSize, snapshot,
                        history(42, 900), history(68, 150), flatHistory(0, 150),
                        history(54, 900), history(12, 900), history(3.5, 900),
                        new EventLogBuffer(), null, 1, true,
                        TimeSeriesBuffer.Range.FIFTEEN_MINUTES, -1, false, true,
                        List.of(), 0, new int[0]));
        String text = Files.readString(
                Path.of("target", "ui-snapshots", "ram-detail-zero-swap-120x35.txt"));
        assertTrue(text.contains("○ swap"));
        assertFalse(text.contains("·█·"), "zero swap must not draw dashed samples");
    }

    @Test
    void drawsSwapDownToZeroBeforeHidingIt() throws IOException {
        TerminalSize size = new TerminalSize(120, 35);
        DashboardSnapshot snapshot = snapshot();
        writeSnapshot("ram-detail-swap-drop", size, (graphics, terminalSize) ->
                DashboardOverviewRenderer.draw(graphics, terminalSize, snapshot,
                        history(42, 900), history(68, 150), swapDropHistory(150),
                        history(54, 900), history(12, 900), history(3.5, 900),
                        new EventLogBuffer(), null, 1, true,
                        TimeSeriesBuffer.Range.FIFTEEN_MINUTES, -1, false, true,
                        List.of(), 0, new int[0]));
        String text = Files.readString(
                Path.of("target", "ui-snapshots", "ram-detail-swap-drop-120x35.txt"));
        assertTrue(text.contains("··"), "swap line should be drawn while in use");
        assertTrue(text.lines().anyMatch(line -> line.contains("0%") && line.contains("·")),
                "swap line should reach the zero baseline before disappearing");
    }

    private static DashboardSnapshot snapshot() {
        CpuSnapshot cpu = new CpuSnapshot("fixture cpu", "x86_64", 1, 42.0, 54.0,
                List.of(new CpuSnapshot.Core(0, 42.0, 2_000_000_000L)));
        return new DashboardSnapshot(Instant.parse("2026-01-01T12:00:00Z"), cpu,
                new MemorySnapshot(68L * 1024 * 1024 * 1024, 16L * 1024 * 1024 * 1024,
                        1L * 1024 * 1024 * 1024, 0), null, null, List.of(), Map.of());
    }

    private static DashboardSnapshot snapshotWithCores() {
        CpuSnapshot cpu = new CpuSnapshot("fixture cpu", "x86_64", 4, 25.0, 54.0,
                List.of(new CpuSnapshot.Core(0, 10.0, 2_000_000_000L),
                        new CpuSnapshot.Core(1, 20.0, 2_000_000_000L),
                        new CpuSnapshot.Core(2, 30.0, 2_000_000_000L),
                        new CpuSnapshot.Core(3, 40.0, 2_000_000_000L)));
        return new DashboardSnapshot(Instant.parse("2026-01-01T12:00:00Z"), cpu,
                new MemorySnapshot(68L * 1024 * 1024 * 1024, 16L * 1024 * 1024 * 1024,
                        1L * 1024 * 1024 * 1024, 0), null, null, List.of(), Map.of());
    }

    private static TimeSeriesBuffer history(double value, int count) {
        TimeSeriesBuffer history = new TimeSeriesBuffer();
        for (int i = 0; i < count; i++) {
            history.append(i % 37 == 0 ? Double.NaN : value + (i % 5));
        }
        return history;
    }

    private static TimeSeriesBuffer flatHistory(double value, int count) {
        TimeSeriesBuffer history = new TimeSeriesBuffer();
        for (int i = 0; i < count; i++) {
            history.append(value);
        }
        return history;
    }

    private static TimeSeriesBuffer swapDropHistory(int count) {
        TimeSeriesBuffer history = new TimeSeriesBuffer();
        for (int i = 0; i < count; i++) {
            history.append(i < count - 30 ? 40.0 : 0.0);
        }
        return history;
    }

    private static void writeSnapshot(String name, TerminalSize size, Drawer drawer) throws IOException {
        DefaultVirtualTerminal terminal = new DefaultVirtualTerminal(size);
        String text;
        try (Screen screen = new TerminalScreen(terminal)) {
            screen.startScreen();
            drawer.draw(screen.newTextGraphics(), size);
            screen.refresh();
            StringBuilder rendered = new StringBuilder();
            for (int row = 0; row < size.getRows(); row++) {
                for (int column = 0; column < size.getColumns(); column++) {
                    rendered.append(terminal.getCharacter(column, row).getCharacter());
                }
                rendered.append(System.lineSeparator());
            }
            text = rendered.toString();
        }
        Path directory = Path.of("target", "ui-snapshots");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(name + "-" + size.getColumns() + "x" + size.getRows() + ".txt"), text);
    }

    @FunctionalInterface
    private interface Drawer {
        void draw(com.googlecode.lanterna.graphics.TextGraphics graphics, TerminalSize size);
    }
}
