package com.ricky.thyme.feature.memory;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.ricky.thyme.ui.Page;

public final class MemoryPage extends Page {
    private final OshiMemoryProvider provider;
    private MemorySnapshot snapshot;
    private String error;

    public MemoryPage(OshiMemoryProvider provider) {
        this.provider = provider;
    }

    @Override
    public String title() {
        return "Memoria";
    }

    @Override
    public void handleKey(KeyStroke key) {}

    @Override
    public void refresh() {
        try {
            snapshot = provider.read();
            error = null;
        } catch (RuntimeException e) {
            error = "Lettura memoria fallita: " + e.getMessage();
        }
    }

    @Override
    public void render(TextGraphics graphics, TerminalSize size) {
        put(graphics, size, 2, "Memoria di sistema (aggiornamento ogni secondo)");
        if (error != null) {
            put(graphics, size, 4, error);
            return;
        }
        if (snapshot == null) {
            put(graphics, size, 4, "Lettura in corso...");
            return;
        }
        put(graphics, size, 4, String.format(
                "RAM usata:  %.1f / %.1f GiB (%.1f%%)",
                gib(snapshot.usedBytes()),
                gib(snapshot.totalBytes()),
                percent(snapshot.usedBytes(), snapshot.totalBytes())));
        put(graphics, size, 5, String.format(
                "RAM libera: %.1f GiB", gib(snapshot.totalBytes() - snapshot.usedBytes())));
        put(graphics, size, 7, String.format(
                "Swap usata: %.1f / %.1f GiB (%.1f%%)",
                gib(snapshot.swapUsedBytes()),
                gib(snapshot.swapTotalBytes()),
                percent(snapshot.swapUsedBytes(), snapshot.swapTotalBytes())));
    }

    private static double gib(long bytes) {
        return bytes / (1024.0 * 1024 * 1024);
    }

    private static double percent(long used, long total) {
        return total == 0 ? 0 : used * 100.0 / total;
    }

    private static void put(TextGraphics graphics, TerminalSize size, int row, String text) {
        if (row < size.getRows()) {
            graphics.putString(0, row, text.substring(0, Math.min(text.length(), size.getColumns())));
        }
    }
}
