package com.ricky.thyme.ui.widgets;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.virtual.DefaultVirtualTerminal;
import com.ricky.thyme.ui.Theme;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StatCardTest {

    @Test
    void drawDoesNotThrowOnNormalInput() throws Exception {
        var term = new DefaultVirtualTerminal(new TerminalSize(24, 5));
        try (var screen = new TerminalScreen(term)) {
            screen.startScreen();
            var g = screen.newTextGraphics();
            assertDoesNotThrow(() -> StatCard.draw(
                    g, 0, 0, 24, 5,
                    "CPU %", "42%", Theme.INK,
                    new double[]{10, 20, 30, 40, 50},
                    "current", true));
        }
    }

    @Test
    void drawDoesNotThrowOnNullHistory() throws Exception {
        var term = new DefaultVirtualTerminal(new TerminalSize(20, 4));
        try (var screen = new TerminalScreen(term)) {
            screen.startScreen();
            assertDoesNotThrow(() -> StatCard.draw(
                    screen.newTextGraphics(), 0, 0, 20, 4,
                    "RAM", "n/a", Theme.INK_MUTED,
                    null, null, false));
        }
    }

    @Test
    void drawTooSmallDoesNotThrow() throws Exception {
        var term = new DefaultVirtualTerminal(new TerminalSize(10, 5));
        try (var screen = new TerminalScreen(term)) {
            screen.startScreen();
            var g = screen.newTextGraphics();
            assertDoesNotThrow(() -> StatCard.draw(
                    g, 0, 0, 2, 1, "CPU", "42%", Theme.INK, new double[]{50}, null, false));
        }
    }

    @Test
    void sparklineSitsBesideTheValueNotOnTheTitleRow() throws Exception {
        var term = new DefaultVirtualTerminal(new TerminalSize(24, 5));
        try (var screen = new TerminalScreen(term)) {
            screen.startScreen();
            StatCard.draw(screen.newTextGraphics(), 0, 0, 24, 5,
                    "CPU", "42%", Theme.INK,
                    new double[]{10, 20, 30, 40, 50}, "current", true);
            screen.refresh();

            assertFalse(hasSparkline(term, 1), "the title row must not contain the sparkline");
            assertTrue(hasSparkline(term, 2), "the sparkline belongs next to the value");
        }
    }

    private static boolean hasSparkline(DefaultVirtualTerminal terminal, int row) {
        for (int column = 0; column < terminal.getTerminalSize().getColumns(); column++) {
            char character = terminal.getCharacter(column, row).getCharacter();
            if (character >= '▁' && character <= '█') {
                return true;
            }
        }
        return false;
    }

    @Test
    void buildSparklineAllSame() {
        double[] h = {50, 50, 50};
        String s = StatCard.buildSparkline(h, 3);
        assertEquals(3, s.length());
        // All chars should be mid-range
        for (char c : s.toCharArray())
            assertEquals(StatCard.buildSparkline(new double[]{50}, 1).charAt(0), c);
    }
}
