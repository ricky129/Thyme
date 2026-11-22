package com.ricky.thyme.feature.containers;

import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.ricky.thyme.feature.telemetry.EventLogBuffer;
import com.ricky.thyme.feature.telemetry.MetricScheduler;
import com.ricky.thyme.feature.telemetry.TimeSeriesBuffer;
import com.ricky.thyme.ui.Page;
import com.ricky.thyme.ui.Theme;
import com.ricky.thyme.ui.widgets.Dock;
import com.ricky.thyme.ui.widgets.Overlay;
import com.ricky.thyme.ui.widgets.Panel;
import com.ricky.thyme.ui.widgets.StatCard;
import com.ricky.thyme.ui.widgets.Table;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class ContainerPage extends Page {
    private static final String[] DOCK_LABELS = {"Home", "Containers"};
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final ContainerProvider provider;
    private final MetricScheduler scheduler = new MetricScheduler();
    private final EventLogBuffer eventLog = new EventLogBuffer();
    private final AtomicReference<List<ContainerSnapshot>> containers = new AtomicReference<>(List.of());
    private final AtomicReference<String> error = new AtomicReference<>();
    private final AtomicReference<String> logs = new AtomicReference<>();
    private final AtomicBoolean loading = new AtomicBoolean();
    private final AtomicInteger selected = new AtomicInteger();
    private final AtomicReference<Executor> background = new AtomicReference<>();
    private boolean details;
    private String pendingAction;
    private final AtomicReference<String> message = new AtomicReference<>("Loading Docker containers...");
    private final AtomicInteger selectedLogLine = new AtomicInteger();
    private Instant lastSampleTime;

    public ContainerPage(ContainerProvider provider) {
        this.provider = provider;
    }

    @Override
    public String title() {
        return "Containers";
    }

    @Override
    public void onShow(Executor background) {
        this.background.set(background);
        scheduler.clear();
        scheduler.register(new TimeSeriesBuffer(), Duration.ofSeconds(5), () -> {
            refresh();
            return Double.NaN;
        });
        scheduler.start();
        refresh();
    }

    @Override
    public void onHide() {
        scheduler.stop();
        background.set(null);
    }

    public EventLogBuffer eventLog() {
        return eventLog;
    }

    @Override
    public void close() {
        scheduler.close();
    }

    @Override
    public boolean capturesGlobalNavigation() {
        return details || pendingAction != null;
    }

    @Override
    public void handleKey(KeyStroke key) {
        if (key.getKeyType() == KeyType.Escape) {
            if (pendingAction != null) {
                pendingAction = null;
                message.set("Action cancelled");
            } else if (details) {
                details = false;
                logs.set(null);
            }
            return;
        }

        if (pendingAction != null) {
            if (key.getKeyType() == KeyType.Character && key.getCharacter() != null) {
                char ch = Character.toLowerCase(key.getCharacter());
                if (ch == 'y') {
                    executePendingAction();
                } else if (ch == 'n') {
                    pendingAction = null;
                    message.set("Action cancelled");
                }
            }
            return;
        }

        if (details) {
            handleDetailKey(key);
            return;
        }

        handleListKey(key);
    }

    private void handleDetailKey(KeyStroke key) {
        if (key.getKeyType() == KeyType.ArrowDown) {
            scrollLogLine(1);
            return;
        }
        if (key.getKeyType() == KeyType.ArrowUp) {
            scrollLogLine(-1);
            return;
        }
        if (key.getKeyType() == KeyType.ArrowLeft) {
            switchContainer(-1);
            return;
        }
        if (key.getKeyType() == KeyType.ArrowRight) {
            switchContainer(1);
            return;
        }

        if (key.getKeyType() != KeyType.Character || key.getCharacter() == null) {
            return;
        }

        char ch = Character.toLowerCase(key.getCharacter());
        switch (ch) {
            case 'j' -> scrollLogLine(1);
            case 'k' -> scrollLogLine(-1);
            case 'h' -> switchContainer(-1);
            case 'l' -> switchContainer(1);
            case 'r' -> requestAction("restart");
            case 's' -> {
                ContainerSnapshot c = selectedContainer();
                if (c != null && "running".equalsIgnoreCase(c.state())) {
                    requestAction("stop");
                }
            }
            case 'a' -> {
                ContainerSnapshot c = selectedContainer();
                if (c != null && !"running".equalsIgnoreCase(c.state())) {
                    requestAction("start");
                }
            }
            case 'b', 'q' -> {
                details = false;
                logs.set(null);
            }
            default -> {
            }
        }
    }

    private void handleListKey(KeyStroke key) {
        if (key.getKeyType() == KeyType.ArrowDown) {
            selected.updateAndGet(val -> Math.min(val + 1, Math.max(0, containers.get().size() - 1)));
            return;
        }
        if (key.getKeyType() == KeyType.ArrowUp) {
            selected.updateAndGet(val -> Math.max(0, val - 1));
            return;
        }
        if (key.getKeyType() == KeyType.Enter) {
            if (!containers.get().isEmpty()) {
                details = true;
                selectedLogLine.set(0);
                logs.set(null);
                loadLogs();
            }
            return;
        }

        if (key.getKeyType() != KeyType.Character || key.getCharacter() == null) {
            return;
        }

        char ch = Character.toLowerCase(key.getCharacter());
        if (ch == 'j') {
            selected.updateAndGet(val -> Math.min(val + 1, Math.max(0, containers.get().size() - 1)));
        } else if (ch == 'k') {
            selected.updateAndGet(val -> Math.max(0, val - 1));
        } else if (ch == 'r') {
            refresh();
        }
    }

    private void scrollLogLine(int delta) {
        String logText = logs.get();
        if (logText == null) {
            return;
        }
        List<String> lines = logText.lines().toList();
        if (lines.isEmpty()) {
            return;
        }
        selectedLogLine.updateAndGet(idx -> Math.max(0, Math.min(idx + delta, lines.size() - 1)));
    }

    private void switchContainer(int delta) {
        List<ContainerSnapshot> list = containers.get();
        if (list.isEmpty()) {
            return;
        }
        int next = (selected.get() + delta + list.size()) % list.size();
        selected.set(next);
        selectedLogLine.set(0);
        logs.set(null);
        loadLogs();
    }

    @Override
    public void refresh() {
        Executor executor = background.get();
        if (executor == null || !loading.compareAndSet(false, true)) {
            return;
        }
        try {
            executor.execute(() -> {
                try {
                    List<ContainerSnapshot> next = provider.list();
                    Map<String, ContainerSnapshot> previous = new HashMap<>();
                    for (ContainerSnapshot item : containers.get()) {
                        previous.put(item.id(), item);
                    }
                    for (ContainerSnapshot item : next) {
                        ContainerSnapshot old = previous.get(item.id());
                        String state = item.state().toLowerCase();
                        if (old != null && !old.state().equalsIgnoreCase(item.state())
                                && ("exited".equals(state) || "stopped".equals(state))) {
                            eventLog.add(new EventLogBuffer.Event(Instant.now(), EventLogBuffer.Severity.WARM,
                                    "Container " + item.name() + " stopped"));
                        }
                        if (old != null && item.restartCount() > old.restartCount()) {
                            eventLog.add(new EventLogBuffer.Event(Instant.now(), EventLogBuffer.Severity.WARM,
                                    "Container " + item.name() + " restarted"));
                        }
                    }
                    containers.set(next);
                    selected.updateAndGet(value -> Math.max(0, Math.min(value, containers.get().size() - 1)));
                    error.set(null);
                    lastSampleTime = Instant.now();
                    message.set(containers.get().size() + " containers • updated");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    error.set(message(e));
                    message.set("Docker unavailable");
                } catch (IOException e) {
                    error.set(message(e));
                    message.set("Docker unavailable");
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
        if (container == null) {
            return;
        }
        pendingAction = action;
        message.set("Confirm " + action + " for " + container.name() + "? Press y / n");
    }

    private void executePendingAction() {
        String action = pendingAction;
        ContainerSnapshot container = selectedContainer();
        pendingAction = null;
        if (action == null || container == null) {
            return;
        }
        Executor executor = background.get();
        if (executor == null) {
            return;
        }
        message.set(action + " in progress...");
        executor.execute(() -> {
            try {
                switch (action) {
                    case "start" -> provider.start(container.id());
                    case "stop" -> provider.stop(container.id());
                    case "restart" -> provider.restart(container.id());
                    default -> throw new IllegalArgumentException("Unsupported container action");
                }
                message.set(action + " completed");
                refresh();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                message.set("Docker action failed: " + message(e));
            } catch (IOException e) {
                message.set("Docker action failed: " + message(e));
            }
        });
    }

    private void loadLogs() {
        ContainerSnapshot container = selectedContainer();
        Executor executor = background.get();
        if (container == null || executor == null) {
            return;
        }
        message.set("Loading recent logs...");
        executor.execute(() -> {
            try {
                String fetched = provider.logs(container.id());
                logs.set(fetched);
                long errCount = fetched.lines().filter(ContainerPage::looksLikeError).count();
                message.set("Recent logs • " + errCount + " error/warning lines");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                message.set("Log read failed: " + message(e));
            } catch (IOException e) {
                message.set("Log read failed: " + message(e));
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

        graphics.setBackgroundColor(Theme.PAGE);
        graphics.fill(' ');

        if (height < 15 || width < 48) {
            Panel.draw(graphics, 0, 0, width, height, "THYME");
            Theme.put(graphics, 2, Math.max(1, height / 2), width - 4,
                    "Terminal too small (" + width + "x" + height + "). Minimum 48x15.", Theme.WARM);
            return;
        }

        drawHeader(graphics, width);

        int footerY = height - 1;
        drawFooter(graphics, width, footerY);

        int contentY = 2;
        int contentHeight = footerY - contentY - 1;

        drawBody(graphics, width, contentY, contentHeight);

        if (details) {
            drawDetailOverlay(graphics, width, height);
        }
        if (pendingAction != null) {
            drawConfirmOverlay(graphics, width, height);
        }
    }

    private void drawHeader(TextGraphics graphics, int width) {
        Theme.put(graphics, 1, 0, 7, "THYME", Theme.ACCENT, true);
        Theme.put(graphics, 9, 0, Math.max(0, width - 10), "Containers", Theme.INK_SECONDARY);

        String currentErr = error.get();
        String healthStatus = currentErr == null ? "● running" : "● error";
        TextColor healthColor = currentErr == null ? Theme.GOOD : Theme.CRITICAL;

        String liveText = "LIVE • " + (lastSampleTime != null
                ? LocalTime.ofInstant(lastSampleTime, java.time.ZoneId.systemDefault()).format(TIME_FMT)
                : "--:--:--");

        String rightStatus = healthStatus + "   " + liveText;
        int rightX = Math.max(20, width - rightStatus.length() - 1);
        if (rightX < width - 1) {
            Theme.put(graphics, rightX, 0, healthStatus.length(), healthStatus, healthColor);
            int liveX = rightX + healthStatus.length() + 3;
            if (liveX < width) {
                Theme.put(graphics, liveX, 0, width - liveX, liveText, Theme.INK_MUTED);
            }
        }

        graphics.setForegroundColor(Theme.HAIRLINE);
        graphics.drawLine(0, 1, width - 1, 1, Theme.H);
    }

    private void drawFooter(TextGraphics graphics, int width, int y) {
        graphics.setForegroundColor(Theme.HAIRLINE);
        graphics.drawLine(0, y, width - 1, y, Theme.H);
        Dock.draw(graphics, 1, y, width - 2, DOCK_LABELS, 1);

        String hints = pendingAction != null
                ? "y confirm  n cancel  Esc close"
                : details
                        ? "←/→ container  ↑↓ select/expand log  a start  s stop  r restart  Esc close"
                        : "↑↓ select  Enter details  r refresh  q quit";
        int hintsX = Math.max(1, width - hints.length() - 1);
        if (hintsX > 25) {
            Theme.put(graphics, hintsX, y, hints.length(), hints, Theme.INK_MUTED);
        }
    }

    private void drawBody(TextGraphics graphics, int width, int y, int height) {
        List<ContainerSnapshot> list = containers.get();
        long total = list.size();
        long running = list.stream().filter(c -> "running".equalsIgnoreCase(c.state())).count();
        long stopped = total - running;
        long restarts = list.stream().mapToInt(ContainerSnapshot::restartCount).sum();

        boolean showKpis = height >= 14 && width >= 60;
        int tableY = y;
        int tableHeight = height;

        if (showKpis) {
            int kpiH = 4;
            int numCards = 4;
            int cardW = Math.max(12, (width - (numCards - 1)) / numCards);
            int kpiX = 0;

            StatCard.draw(graphics, kpiX, y, cardW, kpiH, "Total", String.valueOf(total),
                    Theme.INK, new double[0], "configured", false);
            kpiX += cardW + 1;
            StatCard.draw(graphics, kpiX, y, cardW, kpiH, "Running", String.valueOf(running),
                    running > 0 ? Theme.GOOD : Theme.INK_MUTED, new double[0], "active", false);
            kpiX += cardW + 1;
            StatCard.draw(graphics, kpiX, y, cardW, kpiH, "Stopped", String.valueOf(stopped),
                    stopped > 0 ? Theme.WARM : Theme.INK_MUTED, new double[0], "exited", false);
            kpiX += cardW + 1;
            int lastW = Math.max(12, width - kpiX);
            StatCard.draw(graphics, kpiX, y, lastW, kpiH, "Restarts", String.valueOf(restarts),
                    restarts > 0 ? Theme.CRITICAL : Theme.INK_MUTED, new double[0], "all time", false);

            tableY = y + kpiH + 1;
            tableHeight = height - kpiH - 1;
        }

        Panel.draw(graphics, 0, tableY, width, tableHeight, "Containers · " + total);

        String currentErr = error.get();
        if (currentErr != null) {
            Theme.put(graphics, 2, tableY + 2, width - 4, "Docker unavailable: " + currentErr, Theme.CRITICAL);
            return;
        }
        if (list.isEmpty()) {
            Theme.put(graphics, 2, tableY + 2, width - 4, "No containers found. " + message.get(), Theme.INK_MUTED);
            return;
        }

        int innerX = 2;
        int innerY = tableY + 1;
        int innerW = width - 4;
        int innerH = tableHeight - 2;

        if (innerH < 2 || innerW < 10) {
            return;
        }

        String[] headers;
        int[] colWidths;
        if (innerW >= 120) {
            headers = new String[]{"NAME", "IMAGE", "STATE", "STATUS", "CPU", "MEM", "NET I/O", "RESTARTS"};
            colWidths = new int[]{22, 22, 10, 18, 10, 14, 14, 8};
        } else if (innerW >= 80) {
            headers = new String[]{"NAME", "STATE", "STATUS", "CPU", "MEM", "RESTARTS"};
            colWidths = new int[]{20, 10, 18, 10, 12, 8};
        } else {
            headers = new String[]{"NAME", "STATE", "STATUS", "CPU"};
            colWidths = new int[]{16, 8, 14, 8};
        }

        List<String[]> rows = new ArrayList<>(list.size());
        for (ContainerSnapshot c : list) {
            if (innerW >= 120) {
                rows.add(new String[]{
                        c.name(),
                        c.image(),
                        c.state(),
                        c.status(),
                        blank(c.cpuPercent()),
                        blank(c.memoryUsage()),
                        blank(c.networkIo()),
                        String.valueOf(c.restartCount())
                });
            } else if (innerW >= 80) {
                rows.add(new String[]{
                        c.name(),
                        c.state(),
                        c.status(),
                        blank(c.cpuPercent()),
                        blank(c.memoryUsage()),
                        String.valueOf(c.restartCount())
                });
            } else {
                rows.add(new String[]{
                        c.name(),
                        c.state(),
                        c.status(),
                        blank(c.cpuPercent())
                });
            }
        }

        int sel = selected.get();
        int visibleRows = Math.max(1, innerH - 2);
        int scrollOffset = Math.max(0, Math.min(sel - visibleRows + 1, list.size() - visibleRows));

        Table.draw(graphics, innerX, innerY, innerW, innerH, headers, rows, colWidths, sel, scrollOffset);
    }

    private void drawDetailOverlay(TextGraphics graphics, int termW, int termH) {
        ContainerSnapshot c = selectedContainer();
        if (c == null) {
            return;
        }

        boolean fullWidth = termW < 100;
        int ow = fullWidth ? termW : Math.max(50, Math.min(termW - 4, (int) Math.round(termW * 0.85)));
        int oh = Math.max(11, Math.min(termH - 1, (int) Math.round(termH * 0.90)));
        int ox = fullWidth ? 0 : (termW - ow) / 2;
        int oy = Math.max(2, (termH - oh) / 2 + 1);

        graphics.setBackgroundColor(Theme.SURFACE);
        int fillHeight = fullWidth ? termH - oy : oh;
        graphics.fillRectangle(new TerminalPosition(ox, oy), new TerminalSize(ow, fillHeight), ' ');

        Panel.draw(graphics, ox, oy, ow, oh, c.name() + "  ·  Esc close");

        int ix = ox + 2;
        int iy = oy + 2;
        int iw = ow - 4;

        Theme.put(graphics, ix, iy++, iw, "Image: " + c.image(), Theme.INK_SECONDARY);
        Theme.put(graphics, ix, iy++, iw, "ID: " + c.id(), Theme.INK_MUTED);
        Theme.put(graphics, ix, iy++, iw, "State: " + c.state() + " (" + c.status() + ")",
                "running".equalsIgnoreCase(c.state()) ? Theme.GOOD : Theme.WARM);
        Theme.put(graphics, ix, iy++, iw, "Uptime: " + c.runningFor() + "  •  Restarts: " + c.restartCount()
                + "  •  Exit code: " + (c.exitCode() == null ? "n/a" : c.exitCode()), Theme.INK_SECONDARY);
        Theme.put(graphics, ix, iy++, iw, "CPU: " + blank(c.cpuPercent()) + "  •  Memory: " + blank(c.memoryUsage())
                + " (" + blank(c.memoryPercent()) + ")  •  PIDs: " + blank(c.processCount()), Theme.INK_SECONDARY);
        Theme.put(graphics, ix, iy++, iw, "Network I/O: " + blank(c.networkIo()) + "  •  Block I/O: "
                + blank(c.blockIo()), Theme.INK_SECONDARY);

        iy++;
        String logText = logs.get();
        if (logText == null) {
            Theme.put(graphics, ix, iy++, iw, "Loading recent logs...", Theme.ACCENT);
        } else {
            List<String> logLines = logText.lines().toList();
            Theme.put(graphics, ix, iy++, iw, "Recent logs (" + logLines.size()
                    + " lines, ↑/↓ scroll & expand, ←/→ container):", Theme.ACCENT);

            int availableLogRows = Math.max(1, (oy + oh - 2) - iy);
            drawScrollableLogs(graphics, ix, iy, iw, availableLogRows, logLines);
        }

        String actions = "←/→ container  ↑↓ select/expand log  a start  s stop  r restart  Esc close";
        Theme.put(graphics, ix, oy + oh - 2, iw, actions, Theme.ACCENT);
    }

    private void drawScrollableLogs(TextGraphics graphics, int x, int y, int w, int maxRows, List<String> logLines) {
        if (logLines.isEmpty() || maxRows <= 0) {
            return;
        }

        int sel = Math.max(0, Math.min(selectedLogLine.get(), logLines.size() - 1));

        // Format and measure wrapped lines for each log line around the selection
        List<String> wrappedSelected = wrapText(logLines.get(sel), Math.max(10, w - 2));
        int selectedHeight = Math.min(maxRows, wrappedSelected.size());

        // Determine view start to ensure selected item is fully visible
        int halfRemaining = Math.max(0, (maxRows - selectedHeight) / 2);
        int start = Math.max(0, sel - halfRemaining);
        if (start + (maxRows - selectedHeight + 1) > logLines.size()) {
            start = Math.max(0, logLines.size() - (maxRows - selectedHeight + 1));
        }

        int curY = y;
        int remainingRows = maxRows;

        for (int i = start; i < logLines.size() && remainingRows > 0; i++) {
            boolean isSelected = (i == sel);
            String rawLine = logLines.get(i);
            TextColor color = looksLikeError(rawLine) ? Theme.CRITICAL : Theme.INK_MUTED;

            if (isSelected) {
                // Expanded multi-line view with chevron marker
                for (int sub = 0; sub < wrappedSelected.size() && remainingRows > 0; sub++) {
                    String prefix = (sub == 0) ? "› " : "  ";
                    Theme.put(graphics, x, curY++, w, prefix + wrappedSelected.get(sub), Theme.ACCENT, true);
                    remainingRows--;
                }
            } else {
                // Single line view
                Theme.put(graphics, x + 2, curY++, w - 2, rawLine, color);
                remainingRows--;
            }
        }
    }

    private static List<String> wrapText(String text, int width) {
        String clean = Theme.sanitize(text);
        if (clean.isBlank()) {
            return List.of("");
        }
        if (width <= 0) {
            return List.of(clean);
        }
        List<String> lines = new ArrayList<>();
        int length = clean.length();
        int offset = 0;
        while (offset < length) {
            int end = Math.min(offset + width, length);
            lines.add(clean.substring(offset, end));
            offset = end;
        }
        return lines;
    }

    private void drawConfirmOverlay(TextGraphics graphics, int termW, int termH) {
        ContainerSnapshot c = selectedContainer();
        String cName = c != null ? c.name() : "container";
        List<String> lines = List.of(
                "Are you sure you want to " + pendingAction + " " + cName + "?",
                "",
                "Press 'y' to confirm or 'n' / Esc to cancel."
        );
        Overlay.draw(graphics, termW, termH, 50, 9, "Confirm Action", lines, 0, "y confirm  ·  n cancel");
    }

    private static boolean looksLikeError(String line) {
        return line.matches("(?i).*\\b(error|err|fatal|panic|failed|exception|warning)\\b.*");
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? "n/a" : value;
    }

    private static String message(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }
}
