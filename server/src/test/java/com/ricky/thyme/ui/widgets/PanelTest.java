package com.ricky.thyme.ui.widgets;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.virtual.DefaultVirtualTerminal;
import com.ricky.thyme.ui.Theme;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PanelTest {

    private static DefaultVirtualTerminal terminal(int w, int h) {
        return new DefaultVirtualTerminal(new TerminalSize(w, h));
    }

    @Test
    void drawPanelCorners() throws Exception {
        var term = terminal(20, 6);
        try (var screen = new TerminalScreen(term)) {
            screen.startScreen();
            Panel.draw(screen.newTextGraphics(), 0, 0, 20, 6, "Test");
            screen.refresh();
            assertEquals(Theme.TL, term.getCharacter(0, 0).getCharacter());
            assertEquals(Theme.TR, term.getCharacter(19, 0).getCharacter());
            assertEquals(Theme.BL, term.getCharacter(0, 5).getCharacter());
            assertEquals(Theme.BR, term.getCharacter(19, 5).getCharacter());
        }
    }

    @Test
    void drawPanelTitleAppears() throws Exception {
        var term = terminal(20, 4);
        try (var screen = new TerminalScreen(term)) {
            screen.startScreen();
            Panel.draw(screen.newTextGraphics(), 0, 0, 20, 4, "CPU");
            screen.refresh();
            // Title starts at column 3 (after ╭─)
            char c3 = term.getCharacter(3, 0).getCharacter();
            // The title area should contain part of " CPU "
            // just check it's not the border horizontal character
            assertNotEquals(Theme.H, c3);
        }
    }

    @Test
    void interiorHelpers() {
        assertEquals(1, Panel.interiorX(0));
        assertEquals(2, Panel.interiorY(1));
        assertEquals(8, Panel.interiorW(10));
        assertEquals(3, Panel.interiorH(5));
        assertEquals(0, Panel.interiorW(1));
        assertEquals(0, Panel.interiorH(1));
    }

    @Test
    void drawPanelMinimalSizeDoesNotThrow() throws Exception {
        var term = terminal(10, 5);
        try (var screen = new TerminalScreen(term)) {
            screen.startScreen();
            var g = screen.newTextGraphics();
            assertDoesNotThrow(() -> Panel.draw(g, 0, 0, 2, 2, null));
            assertDoesNotThrow(() -> Panel.draw(g, 0, 0, 1, 1, "X")); // too small, no-op
        }
    }

    @Test
    void drawPanelSupportsCustomBorderColor() throws Exception {
        var term = terminal(20, 4);
        try (var screen = new TerminalScreen(term)) {
            screen.startScreen();
            Panel.draw(screen.newTextGraphics(), 0, 0, 20, 4, "CPU", Theme.ACCENT);
            screen.refresh();
            assertEquals(Theme.ACCENT, term.getCharacter(0, 0).getForegroundColor());
            assertEquals(Theme.ACCENT, term.getCharacter(3, 0).getForegroundColor());
        }
    }
}
