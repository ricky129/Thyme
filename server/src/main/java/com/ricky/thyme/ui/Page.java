package com.ricky.thyme.ui;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class Page implements AutoCloseable {
    private final AtomicBoolean dirty = new AtomicBoolean(true);

    public abstract String title();

    public void onShow(Executor background) {
        // Subclasses may override this hook to start page-specific work.
    }

    public void onHide() {
        // Subclasses may override this hook to release page-specific resources.
    }

    @Override
    public void close() {
        // Pages without timers have nothing to release.
    }

    public final void invalidate() {
        dirty.set(true);
    }

    public final boolean consumeDirty() {
        return dirty.getAndSet(false);
    }

    public abstract void handleKey(KeyStroke key);

    /** Whether the page currently owns keys normally routed by the shell. */
    public boolean capturesGlobalNavigation() {
        return false;
    }

    /** Contextual shortcut lines to display in the help overlay for this page. */
    public java.util.List<String> helpLines() {
        return java.util.List.of();
    }

    public abstract void refresh();

    public abstract void render(TextGraphics graphics, TerminalSize size);
}
