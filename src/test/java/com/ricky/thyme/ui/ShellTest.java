package com.ricky.thyme.ui;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.screen.Screen;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
