package com.ricky.thyme.ui;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import java.io.IOException;
import java.util.concurrent.Executors;

public final class Shell {
    private final Page dashboard;
    private final Page containers;
    private Page page;

    public Shell(Page page) {
        this(page, null);
    }

    public Shell(Page dashboard, Page containers) {
        if (dashboard == null) {
            throw new IllegalArgumentException("la shell richiede una pagina");
        }
        this.dashboard = dashboard;
        this.containers = containers;
        this.page = dashboard;
    }

    public void run() throws IOException {
        try (var background = Executors.newVirtualThreadPerTaskExecutor();
                Screen screen = new TerminalScreen(new DefaultTerminalFactory().createTerminal())) {
            screen.startScreen();
            screen.setCursorPosition(null);
            page.onShow(background);
            long nextRefresh = 0;
            long nextRender = 0;
            boolean running = true;
            try {
                while (running) {
                    KeyStroke key = screen.pollInput();
                    if (key != null) {
                        if (key.getKeyType() == KeyType.Character
                                && key.getCharacter() != null
                                && (key.getCharacter() == 'q' || key.getCharacter() == 'Q')) {
                            running = false;
                        } else if (containers != null
                                && page == containers
                                && key.getKeyType() == KeyType.Escape) {
                            switchPage(background);
                        } else if (containers != null
                                && key.getKeyType() == KeyType.Character
                                && key.getCharacter() != null
                                && (key.getCharacter() == 'd' || key.getCharacter() == 'D')) {
                            switchPage(background);
                        } else {
                            page.handleKey(key);
                        }
                    }

                    long now = System.nanoTime();
                    if (now >= nextRefresh) {
                        page.refresh();
                        nextRefresh = now + 1_000_000_000L;
                    }
                    if (now >= nextRender) {
                        TerminalSize terminalSize = currentTerminalSize(screen);
                        page.render(screen.newTextGraphics(), terminalSize);
                        screen.refresh();
                        nextRender = now + 100_000_000L;
                    }
                    Thread.sleep(30);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                page.onHide();
                screen.stopScreen();
            }
        }
    }

    private void switchPage(java.util.concurrent.Executor background) {
        Page next = page == dashboard ? containers : dashboard;
        page.onHide();
        page = next;
        page.onShow(background);
    }

    static TerminalSize currentTerminalSize(Screen screen) {
        screen.doResizeIfNecessary();
        return screen.getTerminalSize();
    }
}
