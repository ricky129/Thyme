package com.ricky.thyme.ui;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.Screen.RefreshType;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.virtual.DefaultVirtualTerminal;
import java.lang.reflect.Proxy;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShellTest {
    @Test
    void appliesPendingResizeBeforeReadingTerminalSize() {
        TerminalSize initialSize = new TerminalSize(80, 25);
        TerminalSize resizedSize = new TerminalSize(240, 60);
        AtomicBoolean resizeApplied = new AtomicBoolean();
        Screen screen = (Screen) Proxy.newProxyInstance(
                Screen.class.getClassLoader(),
                new Class<?>[] {Screen.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "doResizeIfNecessary" -> {
                        resizeApplied.set(true);
                        yield resizedSize;
                    }
                    case "getTerminalSize" -> resizeApplied.get() ? resizedSize : initialSize;
                    default -> throw new AssertionError("Unexpected screen method: " + method.getName());
                });

        assertEquals(resizedSize, Shell.currentTerminalSize(screen));
        assertTrue(resizeApplied.get());
    }

    @Test
    void rendersWithinTheResizedTerminalDimensions() throws IOException {
        var terminal = new DefaultVirtualTerminal(new TerminalSize(80, 25));
        try (Screen screen = new TerminalScreen(terminal)) {
            screen.startScreen();
            terminal.setTerminalSize(new TerminalSize(40, 12));

            TerminalSize size = Shell.currentTerminalSize(screen);
            screen.newTextGraphics().putString(size.getColumns() - 1, size.getRows() - 1, "X");
            screen.refresh(RefreshType.COMPLETE);

            assertEquals(new TerminalSize(40, 12), size);
            assertEquals('X', terminal.getCharacter(39, 11).getCharacter());
        }
    }

    @Test
    void togglesHelpOverlayWithQuestionMarkAndEsc() {
        Page page = new Page() {
            @Override
            public String title() { return "TestPage"; }
            @Override
            public void handleKey(com.googlecode.lanterna.input.KeyStroke key) {}
            @Override
            public void refresh() {}
            @Override
            public void render(com.googlecode.lanterna.graphics.TextGraphics graphics, TerminalSize size) {}
            @Override
            public java.util.List<String> helpLines() {
                return java.util.List.of("  t Test action");
            }
        };

        Shell shell = new Shell(page);
        assertFalse(shell.isHelpActive());

        // Press '?' opens help
        shell.testHandleKey(new com.googlecode.lanterna.input.KeyStroke('?', false, false), Runnable::run);
        assertTrue(shell.isHelpActive());

        // 'q' does not quit while help is active
        boolean stillRunning = shell.testHandleKey(new com.googlecode.lanterna.input.KeyStroke('q', false, false), Runnable::run);
        assertTrue(stillRunning);
        assertTrue(shell.isHelpActive());

        // Esc closes help
        shell.testHandleKey(new com.googlecode.lanterna.input.KeyStroke(com.googlecode.lanterna.input.KeyType.Escape), Runnable::run);
        assertFalse(shell.isHelpActive());

        // F1 opens help
        shell.testHandleKey(new com.googlecode.lanterna.input.KeyStroke(com.googlecode.lanterna.input.KeyType.F1), Runnable::run);
        assertTrue(shell.isHelpActive());

        // '?' closes help
        shell.testHandleKey(new com.googlecode.lanterna.input.KeyStroke('?', false, false), Runnable::run);
        assertFalse(shell.isHelpActive());
    }
}
