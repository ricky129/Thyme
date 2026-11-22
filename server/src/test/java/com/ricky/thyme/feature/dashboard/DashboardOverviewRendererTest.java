package com.ricky.thyme.feature.dashboard;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.virtual.DefaultVirtualTerminal;
import com.ricky.thyme.feature.cpu.CpuSnapshot;
import com.ricky.thyme.feature.memory.MemorySnapshot;
import com.ricky.thyme.feature.telemetry.EventLogBuffer;
import com.ricky.thyme.feature.telemetry.TimeSeriesBuffer;
import java.time.Instant;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DashboardOverviewRendererTest {
    @Test
    void rendersAllSupportedBreakpoints() throws IOException {
        DashboardSnapshot snapshot = snapshot();
        TimeSeriesBuffer cpu = history(42);
        TimeSeriesBuffer memory = history(68);
        EventLogBuffer events = new EventLogBuffer();
        events.add(new EventLogBuffer.Event(Instant.now(), EventLogBuffer.Severity.WARM, "CPU threshold"));

        for (TerminalSize size : List.of(
                new TerminalSize(48, 15), new TerminalSize(80, 24),
                new TerminalSize(120, 35), new TerminalSize(200, 60))) {
            DefaultVirtualTerminal terminal = new DefaultVirtualTerminal(size);
            try (Screen screen = new TerminalScreen(terminal)) {
                screen.startScreen();
                assertDoesNotThrow(() -> DashboardOverviewRenderer.draw(screen.newTextGraphics(), size,
                        snapshot, cpu, memory, events, null));
                screen.refresh();
                String text = terminalText(terminal);
                assertTrue(text.contains("THYME"));
                assertTrue(text.contains("CPU"));
                assertTrue(text.contains("RAM"));
                assertTrue(text.contains("Temp"));
                assertTrue(text.contains("GPU"));
                assertTrue(text.contains("Disk I/O"));
            }
        }
    }

    @Test
    void computesNonOverlappingCollageTilesAndHitTestingAcrossBreakpoints() {
        for (TerminalSize size : List.of(
                new TerminalSize(48, 15), new TerminalSize(80, 24),
                new TerminalSize(120, 35), new TerminalSize(200, 60))) {
            List<DashboardOverviewRenderer.TileRect> tiles = DashboardOverviewRenderer.computeTiles(size);
            assertEquals(5, tiles.size());
            for (int i = 0; i < tiles.size(); i++) {
                DashboardOverviewRenderer.TileRect tile = tiles.get(i);
                assertEquals(i, tile.cardIndex());
                assertTrue(tile.x() >= 0 && tile.y() >= 2);
                assertTrue(tile.x() + tile.width() <= size.getColumns());
                assertTrue(tile.y() + tile.height() <= size.getRows() - 1);
                assertEquals(i, DashboardOverviewRenderer.hitTestTile(size, tile.x(), tile.y()));
                assertEquals(i, DashboardOverviewRenderer.hitTestTile(
                        size, tile.x() + tile.width() - 1, tile.y() + tile.height() - 1));
                for (int j = i + 1; j < tiles.size(); j++) {
                    DashboardOverviewRenderer.TileRect other = tiles.get(j);
                    boolean separated = tile.x() + tile.width() <= other.x()
                            || other.x() + other.width() <= tile.x()
                            || tile.y() + tile.height() <= other.y()
                            || other.y() + other.height() <= tile.y();
                    assertTrue(separated, "tiles " + i + " and " + j + " must not overlap at " + size);
                }
            }
            assertEquals(-1, DashboardOverviewRenderer.hitTestTile(size, 0, 0));
            assertEquals(-1, DashboardOverviewRenderer.hitTestTile(size, 0, size.getRows() - 1));
        }
    }

    @Test
    void explainsUnsupportedTerminalSize() throws IOException {
        TerminalSize size = new TerminalSize(47, 14);
        DefaultVirtualTerminal terminal = new DefaultVirtualTerminal(size);
        try (Screen screen = new TerminalScreen(terminal)) {
            screen.startScreen();
            DashboardOverviewRenderer.draw(screen.newTextGraphics(), size, null,
                    new TimeSeriesBuffer(), new TimeSeriesBuffer(), new EventLogBuffer(), null);
            screen.refresh();
            assertTrue(terminalText(terminal).contains("at least 48x15"));
        }
    }

    @Test
    void doesNotUseCpuHistoryForMetricsWithoutTheirOwnHistory() throws IOException {
        TerminalSize size = new TerminalSize(120, 35);
        DefaultVirtualTerminal terminal = new DefaultVirtualTerminal(size);
        try (Screen screen = new TerminalScreen(terminal)) {
            screen.startScreen();
            DashboardOverviewRenderer.draw(screen.newTextGraphics(), size, snapshot(), history(42),
                    history(68), null, null, null, new EventLogBuffer(), null, 4, true);
            screen.refresh();
            assertTrue(terminalText(terminal).contains("history unavailable for this metric"));
        }
    }

    @Test
    void rendersOneActiveRangeAndExplainsTheCpuSelector() throws IOException {
        TerminalSize size = new TerminalSize(120, 35);
        DefaultVirtualTerminal terminal = new DefaultVirtualTerminal(size);
        try (Screen screen = new TerminalScreen(terminal)) {
            screen.startScreen();
            DashboardOverviewRenderer.draw(screen.newTextGraphics(), size, snapshot(), history(42),
                    history(68), history(54), new TimeSeriesBuffer(), history(3),
                    new EventLogBuffer(), null, 0, true,
                    com.ricky.thyme.feature.telemetry.TimeSeriesBuffer.Range.ONE_HOUR,
                    -1, false, true);
            screen.refresh();
            String text = terminalText(terminal);
            assertEquals(1, occurrences(text, "[1H]"));
            assertTrue(text.contains("CPU: [Total]  Per-core  (Tab)"));
        }
    }

    private static int occurrences(String text, String value) {
        int count = 0;
        int offset = 0;
        int next = text.indexOf(value, offset);
        while (next >= 0) {
            count++;
            offset = next + value.length();
            next = text.indexOf(value, offset);
        }
        return count;
    }

    private static DashboardSnapshot snapshot() {
        CpuSnapshot cpu = new CpuSnapshot("test cpu", "x86_64", 1, 42.0, 54.0,
                List.of(new CpuSnapshot.Core(0, 42.0, 2_000_000_000L)));
        return new DashboardSnapshot(Instant.now(), cpu, new MemorySnapshot(100, 68, 0, 0),
                null, null, List.of(), Map.of());
    }

    private static TimeSeriesBuffer history(double value) {
        TimeSeriesBuffer history = new TimeSeriesBuffer();
        history.append(value);
        return history;
    }

    private static String terminalText(DefaultVirtualTerminal terminal) {
        StringBuilder text = new StringBuilder();
        TerminalSize size = terminal.getTerminalSize();
        for (int row = 0; row < size.getRows(); row++) {
            for (int column = 0; column < size.getColumns(); column++) {
                text.append(terminal.getCharacter(column, row).getCharacter());
            }
        }
        return text.toString();
    }
}
