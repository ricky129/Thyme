package com.ricky.thyme.feature.services;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.ricky.thyme.ui.Page;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class ServicesPage extends Page {
    private final ServiceProvider manager;
    private final AtomicReference<List<ServiceSnapshot>> services = new AtomicReference<>(List.of());
    private final AtomicBoolean loading = new AtomicBoolean();
    // refresh() and its worker write this while render() reads it on the UI thread;
    // AtomicReference makes worker updates visible to the renderer.
    private final AtomicReference<String> status = new AtomicReference<>("Press r to load services.");
    private int selected;
    private Executor background;

    public ServicesPage(ServiceProvider manager) {
        this.manager = manager;
    }

    @Override
    public String title() {
        return "Services";
    }

    @Override
    public void onShow(Executor background) {
        this.background = background;
        refresh();
    }

    @Override
    public void handleKey(KeyStroke key) {
        if (key.getKeyType() == KeyType.ArrowDown)
            selected = Math.min(selected + 1, Math.max(0, services.get().size() - 1));
        else if (key.getKeyType() == KeyType.ArrowUp)
            selected = Math.max(0, selected - 1);
        else if (key.getKeyType() == KeyType.Character && key.getCharacter() != null) {
            if (Character.toLowerCase(key.getCharacter()) == 'r')
                refresh();
        }
    }

    @Override
    public void refresh() {
        if (background == null || !loading.compareAndSet(false, true))
            return;
        status.set("Loading services...");
        background.execute(() -> {
            try {
                services.set(manager.list());
                status.set(services.get().size() + " services loaded.");
            } catch (Exception e) {
                status.set("Error: " + e.getMessage());
            } finally {
                loading.set(false);
            }
        });
    }

    @Override
    public void render(TextGraphics graphics, TerminalSize size) {
        put(graphics, size, 2, "↑/↓ select  r refresh");
        List<ServiceSnapshot> current = services.get();
        int rows = Math.max(0, size.getRows() - 6);
        for (int i = 0; i < current.size() && i < rows; i++) {
            ServiceSnapshot service = current.get(i);
            String marker = i == selected ? "> " : "  ";
            String line = marker + service.unit() + "  " + service.active() + "/" + service.sub()
                    + "  " + service.description();
            put(graphics, size, 4 + i, line);
        }
        int statusRow = Math.max(2, size.getRows() - 4);
        put(graphics, size, statusRow, status.get());
    }

    private static void put(TextGraphics graphics, TerminalSize size, int row, String text) {
        if (row >= 0 && row < size.getRows())
            graphics.putString(0, row, text.substring(0, Math.min(text.length(), size.getColumns())));
    }
}
