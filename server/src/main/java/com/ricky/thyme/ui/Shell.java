package com.ricky.thyme.ui;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.LockSupport;

public final class Shell {
    private static final long MAX_RENDER_DELAY_NANOS = 1_000_000_000L;

    private final PageRegistry registry;

    public Shell(Page page) {
        this(PageRegistry.of(page));
    }

    public Shell(Page dashboard, Page containers) {
        this(new PageRegistry(java.util.List.of(
                new PageRegistry.Entry("overview", dashboard),
                new PageRegistry.Entry("containers", containers))));
    }

    public Shell(PageRegistry registry) {
        if (registry == null) {
            throw new IllegalArgumentException("the shell requires a page registry");
        }
        this.registry = registry;
    }

    public void run() throws IOException {
        try (var background = Executors.newVirtualThreadPerTaskExecutor();
                Screen screen = new TerminalScreen(new DefaultTerminalFactory().createTerminal())) {
            screen.startScreen();
            screen.setCursorPosition(null);
            registry.active().onShow(background);
            long nextPeriodicRender = 0;
            TerminalSize lastSize = null;
            boolean running = true;
            try {
                while (running) {
                    KeyStroke key = screen.pollInput();
                    if (key != null) {
                        running = handleKey(key, background);
                    }

                    long now = System.nanoTime();
                    TerminalSize terminalSize = currentTerminalSize(screen);
                    boolean resized = !terminalSize.equals(lastSize);
                    if (resized) {
                        lastSize = terminalSize;
                    }
                    if (resized || registry.active().consumeDirty() || now >= nextPeriodicRender) {
                        registry.active().render(screen.newTextGraphics(), terminalSize);
                        screen.refresh();
                        nextPeriodicRender = now + MAX_RENDER_DELAY_NANOS;
                    }
                    LockSupport.parkNanos(30_000_000L);

                    if (Thread.currentThread().isInterrupted()) {
                        running = false;
                    }
                }
            } finally {
                registry.active().onHide();
                registry.close();
                screen.stopScreen();
            }
        }
    }

    private boolean handleKey(KeyStroke key, java.util.concurrent.Executor background) {
        Page previous = registry.active();
        if (key.getKeyType() == KeyType.Character && key.getCharacter() != null
                && (key.getCharacter() == 'q' || key.getCharacter() == 'Q')) {
            if (registry.isRoot()) {
                return false;
            }
            returnToRoot(background, previous);
            return true;
        }
        if (key.getKeyType() == KeyType.Escape && !registry.isRoot()) {
            returnToRoot(background, previous);
            return true;
        }
        if (!registry.active().capturesGlobalNavigation() && registry.handleNavigation(key)) {
            switchToSelected(background, previous);
            return true;
        }
        registry.active().handleKey(key);
        registry.active().invalidate();
        return true;
    }

    private void returnToRoot(java.util.concurrent.Executor background, Page previous) {
        if (registry.returnToRoot()) {
            switchToSelected(background, previous);
        }
    }

    private void switchToSelected(java.util.concurrent.Executor background, Page previous) {
        previous.onHide();
        Page page = registry.active();
        page.onShow(background);
        page.invalidate();
    }

    static TerminalSize currentTerminalSize(Screen screen) {
        screen.doResizeIfNecessary();
        return screen.getTerminalSize();
    }
}
