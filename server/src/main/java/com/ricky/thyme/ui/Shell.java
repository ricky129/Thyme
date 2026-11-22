package com.ricky.thyme.ui;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.input.MouseAction;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.MouseCaptureMode;
import com.ricky.thyme.ui.widgets.Dock;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

public final class Shell {
    private static final long MAX_RENDER_DELAY_NANOS = 1_000_000_000L;

    private final PageRegistry registry;
    private final AtomicBoolean helpActive = new AtomicBoolean(false);
    private TerminalSize lastTerminalSize;

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
                Screen screen = new TerminalScreen(new DefaultTerminalFactory()
                        .setMouseCaptureMode(MouseCaptureMode.CLICK_RELEASE)
                        .createTerminal())) {
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
                    lastTerminalSize = terminalSize;
                    boolean resized = !terminalSize.equals(lastSize);
                    if (resized) {
                        lastSize = terminalSize;
                    }
                    if (resized || registry.active().consumeDirty() || now >= nextPeriodicRender) {
                        TextGraphics g = screen.newTextGraphics();
                        registry.active().render(g, terminalSize);
                        if (helpActive.get()) {
                            drawHelpOverlay(g, terminalSize);
                        }
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
        if (helpActive.get()) {
            if (key instanceof MouseAction mouse) {
                if (mouse.isMouseDown() && mouse.getButton() == 1) {
                    helpActive.set(false);
                    registry.active().invalidate();
                }
                return true;
            }
            if (key.getKeyType() == KeyType.Escape
                    || key.getKeyType() == KeyType.F1
                    || (key.getKeyType() == KeyType.Character && key.getCharacter() != null && key.getCharacter() == '?')) {
                helpActive.set(false);
                registry.active().invalidate();
            }
            return true;
        }

        if (isHelpKey(key) && !registry.active().capturesGlobalNavigation()) {
            helpActive.set(true);
            registry.active().invalidate();
            return true;
        }

        Page previous = registry.active();
        if (key instanceof MouseAction mouse) {
            if (mouse.isMouseDown() && mouse.getButton() == 1
                    && !registry.active().capturesGlobalNavigation()
                    && lastTerminalSize != null
                    && mouse.getPosition() != null
                    && mouse.getPosition().getRow() == lastTerminalSize.getRows() - 1) {
                int clickedPage = Dock.hitTest(
                        1,
                        lastTerminalSize.getRows() - 1,
                        lastTerminalSize.getColumns() - 2,
                        dockLabels(),
                        mouse.getPosition().getColumn(),
                        mouse.getPosition().getRow());
                if (clickedPage >= 0 && registry.select(clickedPage)) {
                    switchToSelected(background, previous);
                    return true;
                }
            }
            registry.active().handleKey(key);
            registry.active().invalidate();
            return true;
        }

        if (key.getKeyType() == KeyType.Character && key.getCharacter() != null
                && (key.getCharacter() == 'q' || key.getCharacter() == 'Q')) {
            if (registry.isRoot()) {
                return false;
            }
            returnToRoot(background, previous);
            return true;
        }
        if (key.getKeyType() == KeyType.Escape && !registry.isRoot()
                && !registry.active().capturesGlobalNavigation()) {
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

    private String[] dockLabels() {
        List<PageRegistry.Entry> entries = registry.entries();
        String[] labels = new String[entries.size()];
        for (int index = 0; index < entries.size(); index++) {
            PageRegistry.Entry entry = entries.get(index);
            if ("overview".equals(entry.id())) {
                labels[index] = "Home";
            } else if ("containers".equals(entry.id())) {
                labels[index] = "Containers";
            } else {
                labels[index] = entry.page().title();
            }
        }
        return labels;
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

    private static boolean isHelpKey(KeyStroke key) {
        return key.getKeyType() == KeyType.F1
                || key.getKeyType() == KeyType.Character && key.getCharacter() != null
                && key.getCharacter() == '?';
    }

    private void drawHelpOverlay(TextGraphics g, TerminalSize size) {
        int termW = size.getColumns();
        int termH = size.getRows();
        java.util.List<String> lines = new java.util.ArrayList<>();
        lines.add("Global Navigation:");
        lines.add("  1..2          Switch view (Overview, Containers)");
        lines.add("  Tab           Cycle next view");
        lines.add("  q             Quit application (from root view)");
        lines.add("  ? / F1        Toggle this help overlay");
        lines.add("  Esc           Close overlay / return to root view");
        lines.add("");
        lines.add(registry.active().title() + " Shortcuts:");
        java.util.List<String> contextual = registry.active().helpLines();
        if (contextual.isEmpty()) {
            lines.add("  No page-specific shortcuts.");
        } else {
            lines.addAll(contextual);
        }

        int maxW = Math.min(68, termW - 4);
        int maxH = Math.min(lines.size() + 4, termH - 2);
        com.ricky.thyme.ui.widgets.Overlay.draw(
                g, termW, termH, maxW, maxH,
                "Help · " + registry.active().title(),
                lines, 0, "Esc / ? close");
    }

    boolean isHelpActive() {
        return helpActive.get();
    }

    boolean testHandleKey(KeyStroke key, java.util.concurrent.Executor background) {
        return handleKey(key, background);
    }

    boolean testHandleKey(KeyStroke key, java.util.concurrent.Executor background, TerminalSize size) {
        lastTerminalSize = size;
        return handleKey(key, background);
    }

    static TerminalSize currentTerminalSize(Screen screen) {
        screen.doResizeIfNecessary();
        return screen.getTerminalSize();
    }
}
