package com.ricky.thyme.feature.containers;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.ricky.thyme.ui.Page;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class ContainerPage extends Page {
    private static final TextColor BACKGROUND = rgb(10, 14, 20);
    private static final TextColor PANEL = rgb(17, 24, 34);
    private static final TextColor BORDER = rgb(44, 57, 72);
    private static final TextColor TEXT = rgb(215, 225, 237);
    private static final TextColor MUTED = rgb(119, 137, 158);
    private static final TextColor TEAL = rgb(74, 222, 199);
    private static final TextColor GREEN = rgb(112, 220, 148);
    private static final TextColor ORANGE = rgb(255, 180, 91);
    private static final TextColor RED = rgb(255, 111, 125);

    private final ContainerProvider provider;
    private final AtomicReference<List<ContainerSnapshot>> containers = new AtomicReference<>(List.of());
    private final AtomicReference<String> error = new AtomicReference<>();
    private final AtomicReference<String> logs = new AtomicReference<>();
    private final AtomicBoolean loading = new AtomicBoolean();
    private final AtomicInteger selected = new AtomicInteger();
    private volatile Executor background;
    private volatile boolean details;
    private volatile String pendingAction;
    private volatile String message = "Loading Docker containers...";
    private volatile int logOffset;

    public ContainerPage(ContainerProvider provider) {
        this.provider = provider;
    }

    @Override
    public String title() {
        return "Containers";
    }

    @Override
    public void onShow(Executor background) {
        this.background = background;
        refresh();
    }

    @Override
    public void onHide() {
        background = null;
    }

    @Override
    public void handleKey(KeyStroke key) {
        if (key.getKeyType() == KeyType.Escape) {
            if (pendingAction != null) {
                pendingAction = null;
                message = "Action cancelled";
            } else {
                details = false;
                logs.set(null);
            }
            return;
        }
        if (key.getKeyType() == KeyType.ArrowDown) {
            if (details && logs.get() != null)
                logOffset++;
            else
                selected.updateAndGet(value -> Math.min(value + 1, Math.max(0, containers.get().size() - 1)));
            return;
        }
        if (key.getKeyType() == KeyType.ArrowUp) {
            if (details && logs.get() != null)
                logOffset = Math.max(0, logOffset - 1);
            else
                selected.updateAndGet(value -> Math.max(0, value - 1));
            return;
        }
        if (key.getKeyType() == KeyType.Enter) {
            details = true;
            logs.set(null);
            return;
        }
        if (key.getKeyType() != KeyType.Character || key.getCharacter() == null)
            return;
        char character = Character.toLowerCase(key.getCharacter());
        if (pendingAction != null) {
            if (character == 'y')
                executePendingAction();
            else if (character == 'n') {
                pendingAction = null;
                message = "Action cancelled";
            }
            return;
        }
        if (character == 'r') {
            if (details)
                requestAction("restart");
            else
                refresh();
        } else if (character == 's' && details && selectedContainer() != null
                && selectedContainer().state().equalsIgnoreCase("running"))
            requestAction("stop");
        else if (character == 'a' && details && selectedContainer() != null
                && !selectedContainer().state().equalsIgnoreCase("running"))
            requestAction("start");
        else if (character == 'l' && details)
            loadLogs();
        else if (character == 'b' || character == 'q') {
            details = false;
            logs.set(null);
        }
    }

    @Override
    public void refresh() {
        Executor executor = background;
        if (executor == null || !loading.compareAndSet(false, true))
            return;
        try {
            executor.execute(() -> {
                try {
                    containers.set(provider.list());
                    selected.updateAndGet(value -> Math.max(0, Math.min(value, containers.get().size() - 1)));
                    error.set(null);
                    message = containers.get().size() + " containers • updated";
                } catch (IOException | InterruptedException e) {
                    if (e instanceof InterruptedException)
                        Thread.currentThread().interrupt();
                    error.set(message(e));
                    message = "Docker unavailable";
                } finally {
                    loading.set(false);
                }
            });
        } catch (RejectedExecutionException e) {
            loading.set(false);
            error.set(message(e));
        }
    }

    private void requestAction(String action) {
        ContainerSnapshot container = selectedContainer();
        if (container == null)
            return;
        pendingAction = action;
        message = "Confirm " + action + " for " + container.name() + "? Press y / n";
    }

    private void executePendingAction() {
        String action = pendingAction;
        ContainerSnapshot container = selectedContainer();
        pendingAction = null;
        if (action == null || container == null)
            return;
        Executor executor = background;
        if (executor == null)
            return;
        message = action + " in progress...";
        executor.execute(() -> {
            try {
                switch (action) {
                    case "start" -> provider.start(container.id());
                    case "stop" -> provider.stop(container.id());
                    case "restart" -> provider.restart(container.id());
                    default -> throw new IllegalArgumentException("Unsupported container action");
                }
                message = action + " completed";
                refresh();
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException)
                    Thread.currentThread().interrupt();
                message = "Docker action failed: " + message(e);
            }
        });
    }

    private void loadLogs() {
        ContainerSnapshot container = selectedContainer();
        Executor executor = background;
        if (container == null || executor == null)
            return;
        message = "Loading recent logs...";
        executor.execute(() -> {
            try {
                logs.set(provider.logs(container.id()));
                logOffset = 0;
                long errors = logs.get().lines().filter(ContainerPage::looksLikeError).count();
                message = "Recent logs • " + errors + " error/warning lines • ↑/↓ scroll";
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException)
                    Thread.currentThread().interrupt();
                message = "Log read failed: " + message(e);
            }
        });
    }

    private ContainerSnapshot selectedContainer() {
        List<ContainerSnapshot> current = containers.get();
        int index = selected.get();
        return index < 0 || index >= current.size() ? null : current.get(index);
    }

    @Override
    public void render(TextGraphics graphics, TerminalSize size) {
        int width = size.getColumns();
        int height = size.getRows();
        graphics.setBackgroundColor(BACKGROUND);
        graphics.setForegroundColor(TEXT);
        graphics.fill(' ');
        put(graphics, 1, 0, width - 2, " THYME  /  CONTAINER MANAGEMENT ", TEAL);
        graphics.setForegroundColor(BORDER);
        if (height > 1) {
            graphics.drawLine(0, 1, width - 1, 1, '─');
            graphics.drawLine(0, height - 1, width - 1, height - 1, '─');
        }
        int footerY = height - 1;
        String footer = pendingAction != null
                ? " y confirm   •   n cancel   •   Esc dashboard "
                : details
                        ? " l logs   •   a start   •   s stop   •   r restart   •   b list   •   Esc dashboard "
                        : " ↑/↓ select   •   Enter details   •   r refresh   •   Esc dashboard ";
        put(graphics, 1, footerY, width - 2, footer, MUTED);
        if (height < 8 || width < 48) {
            put(graphics, 2, 3, width - 4, "Use a larger terminal (minimum 48x8).", ORANGE);
            return;
        }
        if (details)
            drawDetails(graphics, width, height);
        else
            drawList(graphics, width, height);
    }

    private void drawList(TextGraphics graphics, int width, int height) {
        panel(graphics, 0, 2, width, height - 4, "CONTAINERS");
        String currentError = error.get();
        if (currentError != null) {
            put(graphics, 2, 3, width - 4, "Docker unavailable: " + currentError, RED);
            return;
        }
        List<ContainerSnapshot> current = containers.get();
        if (current.isEmpty()) {
            put(graphics, 2, 3, width - 4, "No containers found. " + message, MUTED);
            return;
        }
        put(graphics, 2, 3, width - 4,
                String.format("%-3s %-24s %-12s %-20s %-12s %-9s %-18s %-12s",
                        " ", "NAME", "STATE", "STATUS", "CPU", "MEM", "NET I/O", "RESTARTS"),
                MUTED);
        int visible = Math.max(0, height - 7);
        int start = Math.max(0, Math.min(selected.get() - visible + 1, current.size() - visible));
        for (int offset = 0; offset < visible && start + offset < current.size(); offset++) {
            int index = start + offset;
            ContainerSnapshot container = current.get(index);
            boolean isSelected = index == selected.get();
            String line = String.format("%-3s %-24s %-12s %-20s %-12s %-9s %-18s %-12d",
                    isSelected ? "›" : " ",
                    clip(container.name(), 24),
                    clip(container.state(), 12),
                    clip(container.status(), 20),
                    blank(container.cpuPercent()),
                    blank(container.memoryUsage()),
                    blank(container.networkIo()),
                    container.restartCount());
            put(graphics, 2, 4 + offset, width - 4, line,
                    isSelected ? TEAL : container.state().equalsIgnoreCase("running") ? GREEN : MUTED);
        }
        put(graphics, 2, height - 3, width - 4, message, MUTED);
    }

    private void drawDetails(TextGraphics graphics, int width, int height) {
        panel(graphics, 0, 2, width, height - 4, "CONTAINER DETAILS");
        ContainerSnapshot container = selectedContainer();
        if (container == null) {
            put(graphics, 2, 3, width - 4, "Container no longer exists. Refresh the list.", ORANGE);
            return;
        }
        String[] fields = {
            container.name() + "  •  " + container.image(),
            "ID: " + container.id(),
            "State: " + container.state() + "  •  " + container.status(),
            "Uptime: " + container.runningFor() + "  •  Restarts: " + container.restartCount()
                    + "  •  Last exit: " + (container.exitCode() == null ? "n/a" : container.exitCode()),
            "CPU: " + blank(container.cpuPercent()) + "  •  Memory: " + blank(container.memoryUsage())
                    + " (" + blank(container.memoryPercent()) + ")  •  PIDs: " + blank(container.processCount()),
            "Network I/O: " + blank(container.networkIo()) + "  •  Block I/O: " + blank(container.blockIo()),
        };
        int row = 3;
        for (String field : fields)
            put(graphics, 2, row++, width - 4, field, row == 4 ? TEAL : TEXT);
        String value = logs.get();
        if (value == null) {
            put(graphics, 2, row + 1, width - 4, "Press l to load the latest 200 log lines.", MUTED);
            if (pendingAction != null)
                put(graphics, 2, row + 2, width - 4, message, ORANGE);
            else if (message != null)
                put(graphics, 2, row + 2, width - 4, message, MUTED);
            return;
        }
        List<String> lines = new ArrayList<>(value.lines().toList());
        int logHeight = Math.max(0, height - row - 7);
        int start = Math.max(0, Math.min(logOffset, lines.size() - logHeight));
        put(graphics, 2, row, width - 4, "RECENT LOGS", ORANGE);
        for (int offset = 0; offset < logHeight && start + offset < lines.size(); offset++) {
            String line = lines.get(start + offset);
            put(graphics, 2, row + 1 + offset, width - 4, line, looksLikeError(line) ? RED : MUTED);
        }
        put(graphics, 2, height - 3, width - 4, message, MUTED);
    }

    private static void panel(TextGraphics graphics, int x, int y, int width, int height, String title) {
        graphics.setBackgroundColor(PANEL);
        graphics.fillRectangle(new com.googlecode.lanterna.TerminalPosition(x, y),
                new TerminalSize(width, height), ' ');
        graphics.setForegroundColor(BORDER);
        graphics.drawLine(x, y, x + width - 1, y, '─');
        graphics.drawLine(x, y + height - 1, x + width - 1, y + height - 1, '─');
        graphics.drawLine(x, y, x, y + height - 1, '│');
        graphics.drawLine(x + width - 1, y, x + width - 1, y + height - 1, '│');
        put(graphics, x + 2, y, width - 4, " " + title + " ", TEAL);
    }

    private static boolean looksLikeError(String line) {
        return line.matches("(?i).*\\b(error|err|fatal|panic|failed|exception|warning)\\b.*");
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? "n/a" : value;
    }

    private static String clip(String value, int width) {
        return value.length() <= width ? value : value.substring(0, Math.max(0, width - 1)) + "…";
    }

    private static String message(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    private static TextColor rgb(int red, int green, int blue) {
        return new TextColor.RGB(red, green, blue);
    }

    private static void put(TextGraphics graphics, int x, int y, int width, String text, TextColor color) {
        if (width <= 0 || y < 0 || text == null)
            return;
        String clipped = text.substring(0, Math.min(text.length(), width));
        graphics.setForegroundColor(color);
        graphics.putString(x, y, clipped);
    }
}
