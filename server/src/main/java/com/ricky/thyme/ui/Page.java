package com.ricky.thyme.ui;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import java.util.concurrent.Executor;

public abstract class Page {
    public abstract String title();

    public void onShow(Executor background) {
        // Subclasses may override this hook to start page-specific work.
    }

    public void onHide() {
        // Subclasses may override this hook to release page-specific resources.
    }

    public abstract void handleKey(KeyStroke key);

    public abstract void refresh();

    public abstract void render(TextGraphics graphics, TerminalSize size);
}
