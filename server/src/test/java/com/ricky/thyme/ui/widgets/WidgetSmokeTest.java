package com.ricky.thyme.ui.widgets;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.virtual.DefaultVirtualTerminal;
import com.ricky.thyme.ui.Theme;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WidgetSmokeTest {

    /** Creates a virtual terminal screen for testing at the given dimensions. */
    private static TerminalScreen makeScreen(int w, int h, DefaultVirtualTerminal[] termOut)
            throws Exception {
        termOut[0] = new DefaultVirtualTerminal(new TerminalSize(w, h));
        var screen = new TerminalScreen(termOut[0]);
        screen.startScreen();
        return screen;
    }

    // ── Gauge ────────────────────────────────────────────────────────────────

    @Test
    void gaugeDoesNotThrowNormal() throws Exception {
        var termOut = new DefaultVirtualTerminal[1];
        try (var screen = makeScreen(60, 3, termOut)) {
            assertDoesNotThrow(() -> Gauge.draw(
                    screen.newTextGraphics(), 0, 0, 60, "CPU", 42.0, "42%", Theme.PRIMARY));
        }
    }

    @Test
    void gaugeNullValueShowsNa() throws Exception {
        var termOut = new DefaultVirtualTerminal[1];
        try (var screen = makeScreen(40, 3, termOut)) {
            assertDoesNotThrow(() -> Gauge.draw(
                    screen.newTextGraphics(), 0, 0, 40, "RAM", null, "n/a", Theme.INK_MUTED));
        }
    }

    @Test
    void gaugeTooNarrowDoesNotThrow() throws Exception {
        var termOut = new DefaultVirtualTerminal[1];
        try (var screen = makeScreen(10, 2, termOut)) {
            assertDoesNotThrow(() -> Gauge.draw(
                    screen.newTextGraphics(), 0, 0, 3, "X", 50.0, "50%", Theme.WARM));
        }
    }

    // ── StatusPill ───────────────────────────────────────────────────────────

    @Test
    void statusPillDoesNotThrow() throws Exception {
        var termOut = new DefaultVirtualTerminal[1];
        try (var screen = makeScreen(20, 2, termOut)) {
            assertDoesNotThrow(() ->
                    StatusPill.draw(screen.newTextGraphics(), 0, 0, 20, Theme.GOOD, "good"));
        }
    }

    @Test
    void statusPillDotCharRendered() throws Exception {
        var termOut = new DefaultVirtualTerminal[1];
        try (var screen = makeScreen(20, 2, termOut)) {
            StatusPill.draw(screen.newTextGraphics(), 0, 0, 20, Theme.GOOD, "good");
            screen.refresh();
            assertEquals('●', termOut[0].getCharacter(0, 0).getCharacter());
        }
    }

    // ── SegmentedTabs ────────────────────────────────────────────────────────

    @Test
    void segmentedTabsDoesNotThrow() throws Exception {
        var termOut = new DefaultVirtualTerminal[1];
        try (var screen = makeScreen(40, 2, termOut)) {
            String[] labels = {"15M", "1H", "6H"};
            assertDoesNotThrow(() ->
                    SegmentedTabs.draw(screen.newTextGraphics(), 0, 0, 40, labels, 1));
        }
    }

    // ── Dock ─────────────────────────────────────────────────────────────────

    @Test
    void dockDoesNotThrow() throws Exception {
        var termOut = new DefaultVirtualTerminal[1];
        try (var screen = makeScreen(80, 2, termOut)) {
            String[] pages = {"Overview", "Storage", "Graphics", "Services", "Containers"};
            assertDoesNotThrow(() ->
                    Dock.draw(screen.newTextGraphics(), 0, 0, 80, pages, 0));
        }
    }

    // ── LineChart ────────────────────────────────────────────────────────────

    @Test
    void lineChartDoesNotThrowWithData() throws Exception {
        var termOut = new DefaultVirtualTerminal[1];
        try (var screen = makeScreen(80, 12, termOut)) {
            double[] samples = new double[60];
            for (int i = 0; i < 60; i++) samples[i] = i * 1.5;
            assertDoesNotThrow(() ->
                    LineChart.draw(screen.newTextGraphics(), 0, 0, 80, 12,
                            "CPU · 15M", "%", samples, 100, Theme.PRIMARY,
                            -1, null, false));
        }
    }

    @Test
    void lineChartDoesNotThrowEmpty() throws Exception {
        var termOut = new DefaultVirtualTerminal[1];
        try (var screen = makeScreen(60, 10, termOut)) {
            assertDoesNotThrow(() ->
                    LineChart.draw(screen.newTextGraphics(), 0, 0, 60, 10,
                            "RAM · 1H", "%", new double[]{}, 100, Theme.SECONDARY,
                            -1, null, false));
        }
    }

    @Test
    void lineChartTooSmallDoesNotThrow() throws Exception {
        var termOut = new DefaultVirtualTerminal[1];
        try (var screen = makeScreen(10, 3, termOut)) {
            assertDoesNotThrow(() ->
                    LineChart.draw(screen.newTextGraphics(), 0, 0, 5, 3,
                            "X", "%", new double[]{50}, 100, Theme.PRIMARY,
                            -1, null, false));
        }
    }

    // ── BarChart ─────────────────────────────────────────────────────────────

    @Test
    void barChartDoesNotThrow() throws Exception {
        var termOut = new DefaultVirtualTerminal[1];
        try (var screen = makeScreen(60, 5, termOut)) {
            String[] labels = {"sda", "sdb", "nvme0"};
            double[] vals   = {12.4, 4.1, 0.0};
            String[] fmts   = {"12.4M/s", "4.1M/s", "0.0M/s"};
            assertDoesNotThrow(() ->
                    BarChart.draw(screen.newTextGraphics(), 0, 0, 60, 5,
                            labels, vals, 20.0, fmts, Theme.PRIMARY));
        }
    }

    // ── EventLog ─────────────────────────────────────────────────────────────

    @Test
    void eventLogDoesNotThrow() throws Exception {
        var termOut = new DefaultVirtualTerminal[1];
        try (var screen = makeScreen(60, 10, termOut)) {
            var entries = List.of(
                    new EventLog.Entry("14:03:21", Theme.CRITICAL, "sshd.service failed"),
                    new EventLog.Entry("14:05:00", Theme.WARM,     "CPU above 80%"));
            assertDoesNotThrow(() ->
                    EventLog.draw(screen.newTextGraphics(), 0, 0, 60, 10,
                            "Events · 2", entries));
        }
    }

    @Test
    void eventLogEmptyShowsPlaceholder() throws Exception {
        var termOut = new DefaultVirtualTerminal[1];
        try (var screen = makeScreen(40, 6, termOut)) {
            EventLog.draw(screen.newTextGraphics(), 0, 0, 40, 6, "Events", List.of());
            screen.refresh();
            // Interior row 0 should contain "No events"
            StringBuilder sb = new StringBuilder();
            for (int col = 1; col < 39; col++)
                sb.append(termOut[0].getCharacter(col, 1).getCharacter());
            assertTrue(sb.toString().contains("No events"));
        }
    }

    // ── Overlay ──────────────────────────────────────────────────────────────

    @Test
    void overlayDoesNotThrow() throws Exception {
        var termOut = new DefaultVirtualTerminal[1];
        try (var screen = makeScreen(80, 24, termOut)) {
            assertDoesNotThrow(() ->
                    Overlay.draw(screen.newTextGraphics(), 80, 24, 60, 16,
                            "CPU Detail",
                            List.of("Average: 42%", "Peak: 94%", "Current: 55%"),
                            0, "Esc close"));
        }
    }

    // ── Table ────────────────────────────────────────────────────────────────

    @Test
    void tableDoesNotThrow() throws Exception {
        var termOut = new DefaultVirtualTerminal[1];
        try (var screen = makeScreen(80, 10, termOut)) {
            var rows = List.of(
                    new String[]{"nginx", "active", "running"},
                    new String[]{"sshd",  "active", "running"},
                    new String[]{"cups",  "inactive", "dead"});
            assertDoesNotThrow(() ->
                    Table.draw(screen.newTextGraphics(), 0, 0, 80, 10,
                            new String[]{"Service", "Active", "Sub"},
                            rows, null, 0, 0));
        }
    }

    @Test
    void tableSelectedRowDoesNotThrow() throws Exception {
        var termOut = new DefaultVirtualTerminal[1];
        try (var screen = makeScreen(60, 5, termOut)) {
            var rows = List.of(new String[]{"a", "b"}, new String[]{"c", "d"});
            assertDoesNotThrow(() ->
                    Table.draw(screen.newTextGraphics(), 0, 0, 60, 5,
                            null, rows, null, 1, 0));
        }
    }
}
