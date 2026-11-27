package com.ricky.thyme.feature.containers;

import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.input.MouseAction;
import com.ricky.thyme.feature.telemetry.EventLogBuffer;
import com.ricky.thyme.feature.telemetry.MetricScheduler;
import com.ricky.thyme.feature.telemetry.TimeSeriesBuffer;
import com.ricky.thyme.ui.Page;
import com.ricky.thyme.ui.Theme;
import com.ricky.thyme.ui.widgets.Dock;
import com.ricky.thyme.ui.widgets.Overlay;
import com.ricky.thyme.ui.widgets.Panel;
import com.ricky.thyme.ui.widgets.StatCard;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class ContainerPage extends Page {
    private static final String[] DOCK_LABELS = {"Home", "Containers"};
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final IContainerProvider provider;
    private final ContainerIconCache iconCache;
    private final AtomicBoolean syncingIcons = new AtomicBoolean();
    private final AtomicReference<TerminalSize> lastRenderSize = new AtomicReference<>();
    private final AtomicInteger scrollRow = new AtomicInteger();
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

    private boolean filtering;
    private String filterQuery = "";

    public ContainerPage(IContainerProvider provider) {
        this(provider, ContainerIconCache.inMemory());
    }

    public ContainerPage(IContainerProvider provider, ContainerIconCache iconCache) {
        this.provider = provider;
        this.iconCache = iconCache;
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
        scheduler.register(new TimeSeriesBuffer(), Duration.ofSeconds(60), () -> {
            syncIconsIfNeeded();
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
        return details || pendingAction != null || filtering;
    }

    @Override
    public List<String> helpLines() {
        if (pendingAction != null) {
            return List.of(
                    "  y             Confirm " + pendingAction,
                    "  n / Esc       Cancel action"
            );
        }
        if (details) {
            return List.of(
                    "  ←/→ or h/l    Switch to previous / next container",
                    "  ↑/↓ or j/k    Select & expand recent log line",
                    "  a             Start container (if stopped)",
                    "  s             Stop container (if running)",
                    "  r             Restart container",
                    "  Esc / b       Close log viewer and return to list"
            );
        }
        if (filtering) {
            return List.of(
                    "  Type query    Filter containers by name, image or state",
                    "  Enter         Apply current filter and navigate filtered list",
                    "  Esc           Cancel filter and show all containers",
                    "  Backspace     Delete character"
            );
        }
        return List.of(
                "  ←↑↓→ / hjkl   Select container card",
                "  Enter / Click Open container details & view recent logs",
                "  /             Search / filter containers",
                "  r             Refresh containers list now",
                "  1..2 / Tab    Switch to another page"
        );
    }

    @Override
    public void handleKey(KeyStroke key) {
        if (key instanceof MouseAction mouse) {
            handleMouse(mouse);
            return;
        }
        if (key.getKeyType() == KeyType.Escape) {
            if (filtering) {
                filtering = false;
                filterQuery = "";
                selected.set(0);
            } else if (pendingAction != null) {
                pendingAction = null;
                message.set("Action cancelled");
            } else if (details) {
                details = false;
                logs.set(null);
            } else if (!filterQuery.isEmpty()) {
                filterQuery = "";
                selected.set(0);
            }
            return;
        }

        if (filtering) {
            handleFilterKey(key);
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

    private void handleMouse(MouseAction mouse) {
        if (!mouse.isMouseDown() || mouse.getButton() != 1 || filtering || pendingAction != null) {
            return;
        }
        TerminalSize size = lastRenderSize.get();
        if (size == null || mouse.getPosition() == null) {
            return;
        }
        int col = mouse.getPosition().getColumn();
        int row = mouse.getPosition().getRow();
        if (details) {
            OverlayRect rect = computeDetailRect(size);
            if (!rect.contains(col, row) || row == rect.y()) {
                details = false;
                logs.set(null);
            }
            return;
        }
        int hit = hitTestCard(size, filteredContainers().size(), scrollRow.get(), col, row);
        if (hit >= 0) {
            selected.set(hit);
            details = true;
            selectedLogLine.set(0);
            logs.set(null);
            loadLogs();
        }
    }

    private int columns() {
        TerminalSize size = lastRenderSize.get();
        return size == null ? 2 : gridColumns(size.getColumns());
    }

    private void moveSelection(int dx, int dy) {
        int count = filteredContainers().size();
        int cols = columns();
        selected.updateAndGet(value -> {
            if (dx != 0) {
                return Math.max(0, Math.min(Math.max(0, count - 1), value + dx));
            }
            if (dy < 0) {
                return value >= cols ? value - cols : value;
            }
            int next = value + cols;
            if (next < count) {
                return next;
            }
            int lastRowStart = Math.max(0, (count - 1) / cols * cols);
            return value < lastRowStart ? count - 1 : value;
        });
    }

    private void handleListKey(KeyStroke key) {
        switch (key.getKeyType()) {
            case ArrowDown -> moveSelection(0, 1);
            case ArrowUp -> moveSelection(0, -1);
            case ArrowLeft -> moveSelection(-1, 0);
            case ArrowRight -> moveSelection(1, 0);
            case Enter -> {
                if (!filteredContainers().isEmpty()) {
                    details = true;
                    selectedLogLine.set(0);
                    logs.set(null);
                    loadLogs();
                }
            }
            case Character -> handleListCharacter(key);
            default -> {
            }
        }
    }

    private void handleListCharacter(KeyStroke key) {
        if (key.getCharacter() == null) {
            return;
        }
        char ch = Character.toLowerCase(key.getCharacter());
        if (ch == 'j') {
            moveSelection(0, 1);
        } else if (ch == 'k') {
            moveSelection(0, -1);
        } else if (ch == 'h') {
            moveSelection(-1, 0);
        } else if (ch == 'l') {
            moveSelection(1, 0);
        } else if (ch == 'r') {
            refresh();
        } else if (ch == '/') {
            filtering = true;
        }
    }

    private void handleFilterKey(KeyStroke key) {
        if (key.getKeyType() == KeyType.Enter) {
            filtering = false;
            return;
        }
        if (key.getKeyType() == KeyType.Backspace) {
            if (!filterQuery.isEmpty()) {
                filterQuery = filterQuery.substring(0, filterQuery.length() - 1);
                selected.set(0);
            }
            return;
        }
        Character c = key.getCharacter();
        if (c != null && !Character.isISOControl(c)) {
            filterQuery += c;
            selected.set(0);
        }
    }

    private List<ContainerSnapshot> filteredContainers() {
        List<ContainerSnapshot> list = containers.get();
        if (filterQuery.isEmpty()) {
            return list;
        }
        String q = filterQuery.toLowerCase(Locale.ROOT);
        List<ContainerSnapshot> result = new ArrayList<>();
        for (ContainerSnapshot c : list) {
            if ((c.name() != null && c.name().toLowerCase(Locale.ROOT).contains(q))
                    || (c.image() != null && c.image().toLowerCase(Locale.ROOT).contains(q))
                    || (c.state() != null && c.state().toLowerCase(Locale.ROOT).contains(q))) {
                result.add(c);
            }
        }
        return result;
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
        List<ContainerSnapshot> list = filteredContainers();
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
                        String state = item.state().toLowerCase(Locale.ROOT);
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
                    syncIconsIfNeeded();
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

    private void syncIconsIfNeeded() {
        Executor executor = background.get();
        if (executor == null || !iconCache.needsSync(containers.get())
                || !syncingIcons.compareAndSet(false, true)) {
            return;
        }
        try {
            executor.execute(() -> {
                try {
                    if (iconCache.sync(containers.get())) {
                        invalidate();
                    }
                } finally {
                    syncingIcons.set(false);
                }
            });
        } catch (RejectedExecutionException e) {
            syncingIcons.set(false);
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

    ContainerSnapshot selectedContainer() {
        List<ContainerSnapshot> current = filteredContainers();
        int index = selected.get();
        return index < 0 || index >= current.size() ? null : current.get(index);
    }

    void setContainers(List<ContainerSnapshot> list) {
        this.containers.set(list);
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

        lastRenderSize.set(size);
        drawBody(graphics, size);

        if (details) {
            drawDetailOverlay(graphics, width, height);
        }
        if (pendingAction != null) {
            drawConfirmOverlay(graphics, width, height);
        }
    }

    private void drawHeader(TextGraphics graphics, int width) {
        Theme.put(graphics, 1, 0, 7, "THYME", Theme.ACCENT, true);
        int total = containers.get().size();
        int shown = filteredContainers().size();
        String title = filterQuery.isEmpty() ? "Containers · " + total
                : "Containers · " + shown + "/" + total + " (filtered)";
        Theme.put(graphics, 9, 0, Math.max(0, width - 10), title, Theme.INK_SECONDARY);

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

        String hints;
        if (pendingAction != null) {
            hints = "y confirm  n cancel  Esc close";
        } else if (details) {
            hints = "←/→ container  ↑↓ select/expand log  a start  s stop  r restart  Esc close";
        } else if (filtering) {
            hints = "Filter: " + filterQuery + "_   (Enter apply · Esc cancel)";
        } else if (!filterQuery.isEmpty()) {
            hints = "Filter: " + filterQuery + "   (/ edit · Esc clear)   ↑↓ select  Enter details";
        } else {
            hints = "←↑↓→ select  Click/Enter details  / filter  r refresh  q quit";
        }
        int hintsX = Math.max(1, width - hints.length() - 1);
        if (hintsX > 25) {
            TextColor hintColor = filtering ? Theme.ACCENT : Theme.INK_MUTED;
            Theme.put(graphics, hintsX, y, hints.length(), hints, hintColor);
        }
    }

    /** Bounds of one container card in terminal coordinates. */
    record CardRect(int containerIndex, int x, int y, int width, int height) {
        boolean contains(int col, int row) {
            return col >= x && col < x + width && row >= y && row < y + height;
        }
    }

    /** Bounds of the detail overlay. */
    record OverlayRect(int x, int y, int width, int height) {
        boolean contains(int col, int row) {
            return col >= x && col < x + width && row >= y && row < y + height;
        }
    }

    private static final int CARD_HEIGHT = 5;
    private static final int COLUMN_GAP = 1;

    static int gridColumns(int width) {
        if (width < 78) {
            return 2;
        }
        if (width < 112) {
            return 3;
        }
        return width < 160 ? 4 : 5;
    }

    private static boolean showsKpis(TerminalSize size) {
        return size.getRows() - 4 >= 14 && size.getColumns() >= 60;
    }

    private static int gridY(TerminalSize size) {
        return showsKpis(size) ? 7 : 2;
    }

    private static int gridHeight(TerminalSize size) {
        return size.getRows() - 1 - gridY(size);
    }

    private static int rowGap(TerminalSize size) {
        return gridHeight(size) >= 18 ? 1 : 0;
    }

    static int visibleRows(TerminalSize size) {
        int gap = rowGap(size);
        return Math.max(1, (gridHeight(size) + gap) / (CARD_HEIGHT + gap));
    }

    static List<CardRect> computeCards(TerminalSize size, int containerCount, int scrollRow) {
        if (size == null || size.getColumns() < 48 || size.getRows() < 15) {
            return List.of();
        }
        int width = size.getColumns();
        int cols = gridColumns(width);
        int gap = rowGap(size);
        int baseWidth = (width - COLUMN_GAP * (cols - 1)) / cols;
        int first = scrollRow * cols;
        int last = Math.min(containerCount, (scrollRow + visibleRows(size)) * cols);
        List<CardRect> cards = new ArrayList<>();
        for (int index = first; index < last; index++) {
            int column = index % cols;
            int x = column * (baseWidth + COLUMN_GAP);
            int w = column == cols - 1 ? width - x : baseWidth;
            int y = gridY(size) + (index / cols - scrollRow) * (CARD_HEIGHT + gap);
            cards.add(new CardRect(index, x, y, w, CARD_HEIGHT));
        }
        return List.copyOf(cards);
    }

    static int hitTestCard(TerminalSize size, int containerCount, int scrollRow, int col, int row) {
        for (CardRect card : computeCards(size, containerCount, scrollRow)) {
            if (card.contains(col, row)) {
                return card.containerIndex();
            }
        }
        return -1;
    }

    static OverlayRect computeDetailRect(TerminalSize size) {
        int termW = size.getColumns();
        int termH = size.getRows();
        boolean fullWidth = termW < 100;
        int ow = fullWidth ? termW : Math.max(50, Math.min(termW - 4, (int) Math.round(termW * 0.85)));
        int oh = Math.max(11, Math.min(termH - 1, (int) Math.round(termH * 0.90)));
        int ox = fullWidth ? 0 : (termW - ow) / 2;
        int oy = Math.max(2, (termH - oh) / 2 + 1);
        return new OverlayRect(ox, oy, ow, oh);
    }

    private void drawBody(TextGraphics graphics, TerminalSize size) {
        int width = size.getColumns();
        int y = 2;
        List<ContainerSnapshot> all = containers.get();
        List<ContainerSnapshot> list = filteredContainers();
        long total = all.size();
        long running = all.stream().filter(c -> "running".equalsIgnoreCase(c.state())).count();
        long stopped = total - running;
        long restarts = all.stream().mapToInt(ContainerSnapshot::restartCount).sum();

        if (showsKpis(size)) {
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
        }

        int gridY = gridY(size);
        String currentErr = error.get();
        if (currentErr != null || list.isEmpty()) {
            Panel.draw(graphics, 0, gridY, width, gridHeight(size),
                    filterQuery.isEmpty() ? "Containers · " + total
                    : "Containers · " + list.size() + "/" + total + " (filtered)");
            String text;
            TextColor color = Theme.INK_MUTED;
            if (currentErr != null) {
                text = "Docker unavailable: " + currentErr;
                color = Theme.CRITICAL;
            } else if (filterQuery.isEmpty()) {
                text = "No containers found. " + message.get();
            } else {
                text = "No containers match '" + filterQuery + "'. Press Esc to clear filter.";
            }
            Theme.put(graphics, 2, gridY + 2, width - 4, text, color);
            return;
        }

        int cols = gridColumns(width);
        int rowsVisible = visibleRows(size);
        int selectedRow = selected.get() / cols;
        int scroll = scrollRow.get();
        if (selectedRow < scroll) {
            scroll = selectedRow;
        } else if (selectedRow >= scroll + rowsVisible) {
            scroll = selectedRow - rowsVisible + 1;
        }
        int totalRows = (list.size() + cols - 1) / cols;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, totalRows - rowsVisible)));
        scrollRow.set(scroll);

        for (CardRect card : computeCards(size, list.size(), scroll)) {
            drawContainerCard(graphics, card, list.get(card.containerIndex()),
                    card.containerIndex() == selected.get());
        }
    }

    private void drawContainerCard(TextGraphics graphics, CardRect card, ContainerSnapshot c,
            boolean isSelected) {
        graphics.setBackgroundColor(Theme.PAGE);
        Panel.draw(graphics, card.x(), card.y(), card.width(), card.height(), null,
                isSelected ? Theme.ACCENT : Theme.HAIRLINE);
        int budget = card.width() - 4;
        if (budget > 0) {
            String title = (isSelected ? " ▸ " : " ") + c.name() + " › ";
            Theme.put(graphics, card.x() + 2, card.y(), budget, Theme.clipToWidth(title, budget),
                    isSelected ? Theme.ACCENT : Theme.INK_SECONDARY, true);
        }
        int ix = Panel.interiorX(card.x());
        int iy = Panel.interiorY(card.y());
        int iw = Panel.interiorW(card.width());
        boolean isRunning = "running".equalsIgnoreCase(c.state());
        ContainerIconCache.Icon icon = iconCache.resolve(c);
        TextColor iconColor = icon.placeholder() ? Theme.INK_MUTED
                : isRunning ? Theme.CPU_SERIES : Theme.WARM;
        String[] rows = icon.rows(Theme.BRAILLE);
        for (int row = 0; row < rows.length; row++) {
            Theme.put(graphics, ix + 1, iy + row, 6, rows[row], iconColor);
        }
        int metaX = ix + 8;
        int metaW = iw - 8;
        if (metaW <= 0) {
            return;
        }
        String state = (isRunning ? "● " : "○ ") + c.state();
        if (c.restartCount() > 0 && state.length() + 5 <= metaW) {
            state = state + " ↻" + c.restartCount();
        }
        Theme.put(graphics, metaX, iy, metaW, state, isRunning ? Theme.GOOD : Theme.WARM);
        Theme.put(graphics, metaX, iy + 1, metaW, "CPU " + blank(c.cpuPercent()),
                Theme.INK_SECONDARY);
        Theme.put(graphics, metaX, iy + 2, metaW, "MEM " + blank(c.memoryUsage()),
                Theme.INK_MUTED);
    }

    private void drawDetailOverlay(TextGraphics graphics, int termW, int termH) {
        ContainerSnapshot c = selectedContainer();
        if (c == null) {
            return;
        }

        OverlayRect rect = computeDetailRect(new TerminalSize(termW, termH));
        boolean fullWidth = termW < 100;
        int ow = rect.width();
        int oh = rect.height();
        int ox = rect.x();
        int oy = rect.y();

        graphics.setBackgroundColor(Theme.SURFACE);
        int fillHeight = fullWidth ? termH - oy : oh;
        graphics.fillRectangle(new TerminalPosition(ox, oy), new TerminalSize(ow, fillHeight), ' ');

        Panel.draw(graphics, ox, oy, ow, oh, c.name() + "  ·  Esc close");

        int ix = ox + 2;
        int iy = oy + 2;
        int iw = ow - 4;

        boolean showIcon = ow >= 60;
        int headW = showIcon ? iw - 8 : iw;
        if (showIcon) {
            ContainerIconCache.Icon icon = iconCache.resolve(c);
            String[] iconRows = icon.rows(Theme.BRAILLE);
            for (int row = 0; row < iconRows.length; row++) {
                Theme.put(graphics, ox + ow - 9, iy + row, 6, iconRows[row],
                        icon.placeholder() ? Theme.INK_MUTED : Theme.CPU_SERIES);
            }
        }
        Theme.put(graphics, ix, iy, headW, "Image: " + c.image(), Theme.INK_SECONDARY);
        iy++;
        Theme.put(graphics, ix, iy, headW, "ID: " + c.id(), Theme.INK_MUTED);
        iy++;
        Theme.put(graphics, ix, iy, headW, "State: " + c.state() + " (" + c.status() + ")",
                "running".equalsIgnoreCase(c.state()) ? Theme.GOOD : Theme.WARM);
        iy++;
        Theme.put(graphics, ix, iy, iw, "Uptime: " + c.runningFor() + "  •  Restarts: " + c.restartCount()
                + "  •  Exit code: " + (c.exitCode() == null ? "n/a" : c.exitCode()), Theme.INK_SECONDARY);
        iy++;
        Theme.put(graphics, ix, iy, iw, "CPU: " + blank(c.cpuPercent()) + "  •  Memory: " + blank(c.memoryUsage())
                + " (" + blank(c.memoryPercent()) + ")  •  PIDs: " + blank(c.processCount()), Theme.INK_SECONDARY);
        iy++;
        Theme.put(graphics, ix, iy, iw, "Network I/O: " + blank(c.networkIo()) + "  •  Block I/O: "
                + blank(c.blockIo()), Theme.INK_SECONDARY);
        iy++;

        iy++;
        String logText = logs.get();
        if (logText == null) {
            Theme.put(graphics, ix, iy, iw, "Loading recent logs...", Theme.ACCENT);
            iy++;
        } else {
            List<String> logLines = logText.lines().toList();
            Theme.put(graphics, ix, iy, iw, "Recent logs (" + logLines.size()
                    + " lines, ↑/↓ scroll & expand, ←/→ container):", Theme.ACCENT);
            iy++;

            int availableLogRows = Math.max(1, oy + oh - 2 - iy);
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
        if (start + maxRows - selectedHeight + 1 > logLines.size()) {
            start = Math.max(0, logLines.size() - (maxRows - selectedHeight + 1));
        }

        int curY = y;
        int remainingRows = maxRows;

        for (int i = start; i < logLines.size() && remainingRows > 0; i++) {
            boolean isSelected = i == sel;
            String rawLine = logLines.get(i);
            TextColor color = looksLikeError(rawLine) ? Theme.CRITICAL : Theme.INK_MUTED;

            if (isSelected) {
                // Expanded multi-line view with chevron marker
                for (int sub = 0; sub < wrappedSelected.size() && remainingRows > 0; sub++) {
                    String prefix = (sub == 0) ? "› " : "  ";
                    Theme.put(graphics, x, curY, w, prefix + wrappedSelected.get(sub), Theme.ACCENT, true);
                    curY++;
                    remainingRows--;
                }
            } else {
                // Single line view
                Theme.put(graphics, x + 2, curY, w - 2, rawLine, color);
                curY++;
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
