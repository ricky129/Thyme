package com.ricky.thyme.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ThemeTest {

    @Test
    void sanitizeStripsAnsiAndControlCharacters() {
        assertEquals("hello world", Theme.sanitize("\u001b[31mhello \u001b[0mworld"));
        assertEquals("tab    space", Theme.sanitize("tab\tspace"));
        assertEquals("clean", Theme.sanitize("\u001b[1;32mclean\u0000\u0007"));
    }

    // ── clipToWidth ──────────────────────────────────────────────────────────

    @Test
    void clipToWidthNoTruncationNeeded() {
        assertEquals("hello", Theme.clipToWidth("hello", 10));
    }

    @Test
    void clipToWidthExactFit() {
        assertEquals("hello", Theme.clipToWidth("hello", 5));
    }

    @Test
    void clipToWidthTruncatesWithEllipsis() {
        String result = Theme.clipToWidth("hello world", 8);
        assertEquals(8, result.codePointCount(0, result.length()),
                "clipped string must fit in maxWidth code-points");
        assertTrue(result.endsWith("…"), "truncated string must end with ellipsis");
    }

    @Test
    void clipToWidthWidthOne() {
        String result = Theme.clipToWidth("abcde", 1);
        assertEquals(1, result.codePointCount(0, result.length()));
        assertEquals("a", result);
    }

    @Test
    void clipToWidthZeroReturnsEmpty() {
        assertEquals("", Theme.clipToWidth("hello", 0));
    }

    @Test
    void clipToWidthNull() {
        assertEquals("", Theme.clipToWidth(null, 10));
    }

    // ── utilizationColor ────────────────────────────────────────────────────

    @Test
    void utilizationColorNull() {
        assertEquals(Theme.INK_MUTED, Theme.utilizationColor(null));
    }

    @Test
    void utilizationColorNormal() {
        assertEquals(Theme.INK, Theme.utilizationColor(50.0));
    }

    @Test
    void utilizationColorWarning() {
        assertEquals(Theme.WARM, Theme.utilizationColor(80.0));
        assertEquals(Theme.WARM, Theme.utilizationColor(94.9));
    }

    @Test
    void utilizationColorCritical() {
        assertEquals(Theme.CRITICAL, Theme.utilizationColor(95.0));
        assertEquals(Theme.CRITICAL, Theme.utilizationColor(100.0));
    }

    // ── filesystemColor ──────────────────────────────────────────────────────

    @Test
    void filesystemColorWarn() {
        assertEquals(Theme.WARM, Theme.filesystemColor(85.0));
        assertEquals(Theme.WARM, Theme.filesystemColor(94.9));
    }

    @Test
    void filesystemColorCritical() {
        assertEquals(Theme.CRITICAL, Theme.filesystemColor(95.0));
    }

    @Test
    void filesystemColorNormal() {
        assertEquals(Theme.INK, Theme.filesystemColor(50.0));
    }

    // ── temperatureColor ─────────────────────────────────────────────────────

    @Test
    void temperatureColorWarn() {
        assertEquals(Theme.WARM, Theme.temperatureColor(80.0));
    }

    @Test
    void temperatureColorCritical() {
        assertEquals(Theme.CRITICAL, Theme.temperatureColor(90.0));
    }

    @Test
    void temperatureColorNormal() {
        assertEquals(Theme.INK, Theme.temperatureColor(60.0));
    }

    // ── drawPanel (smoke test via VirtualTerminal) ───────────────────────────

    @Test
    void drawPanelPopulatesCorners() {
        var terminal = new com.googlecode.lanterna.terminal.virtual.DefaultVirtualTerminal(
                new com.googlecode.lanterna.TerminalSize(20, 6));
        try (var screen = new com.googlecode.lanterna.screen.TerminalScreen(terminal)) {
            screen.startScreen();
            var g = screen.newTextGraphics();
            Theme.drawPanel(g, 0, 0, 20, 6, Theme.HAIRLINE);
            screen.refresh();
            assertEquals(Theme.TL, terminal.getCharacter(0, 0).getCharacter());
            assertEquals(Theme.TR, terminal.getCharacter(19, 0).getCharacter());
            assertEquals(Theme.BL, terminal.getCharacter(0, 5).getCharacter());
            assertEquals(Theme.BR, terminal.getCharacter(19, 5).getCharacter());
        } catch (Exception e) {
            fail("drawPanel threw: " + e);
        }
    }

    @Test
    void drawPanelTooSmallDoesNotThrow() {
        var terminal = new com.googlecode.lanterna.terminal.virtual.DefaultVirtualTerminal(
                new com.googlecode.lanterna.TerminalSize(10, 5));
        try (var screen = new com.googlecode.lanterna.screen.TerminalScreen(terminal)) {
            screen.startScreen();
            var g = screen.newTextGraphics();
            assertDoesNotThrow(() -> Theme.drawPanel(g, 0, 0, 1, 1, Theme.HAIRLINE));
        } catch (Exception e) {
            fail("setup threw: " + e);
        }
    }
}
