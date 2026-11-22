package com.ricky.thyme.feature.dashboard;

import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.ricky.thyme.feature.cpu.CpuSnapshot;
import com.ricky.thyme.feature.cpu.OshiCpuProvider;
import com.ricky.thyme.feature.disk.DiskProvider;
import com.ricky.thyme.feature.disk.DiskSnapshot;
import com.ricky.thyme.feature.gpu.GpuProvider;
import com.ricky.thyme.feature.gpu.GpuReading;
import com.ricky.thyme.feature.gpu.GpuSnapshot;
import com.ricky.thyme.feature.host.HostProvider;
import com.ricky.thyme.feature.host.HostSnapshot;
import com.ricky.thyme.feature.memory.MemorySnapshot;
import com.ricky.thyme.feature.memory.OshiMemoryProvider;
import com.ricky.thyme.feature.services.ServiceProvider;
import com.ricky.thyme.feature.services.ServiceSnapshot;
import com.ricky.thyme.feature.telemetry.EventLogBuffer;
import com.ricky.thyme.feature.telemetry.EventTransitionTracker;
import com.ricky.thyme.feature.telemetry.MetricScheduler;
import com.ricky.thyme.feature.telemetry.TimeSeriesBuffer;
import com.ricky.thyme.ui.Page;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class DashboardPage extends Page {

    private static final TextColor BACKGROUND = rgb(10, 14, 20);
    private static final TextColor PANEL = rgb(17, 24, 34);
    private static final TextColor BORDER = rgb(44, 57, 72);
    private static final TextColor TEXT = rgb(215, 225, 237);
    private static final TextColor MUTED = rgb(119, 137, 158);
    private static final TextColor TEAL = rgb(74, 222, 199);
    private static final TextColor BLUE = rgb(105, 173, 255);
    private static final TextColor PURPLE = rgb(190, 145, 255);
    private static final TextColor ORANGE = rgb(255, 180, 91);
    private static final TextColor GREEN = rgb(112, 220, 148);
    private static final TextColor RED = rgb(255, 111, 125);
    private static final char[] SPARKLINE = {'▁', '▂', '▃', '▄', '▅', '▆', '▇', '█'};
    private static final String BAR_FULL = "█";
    private static final String BAR_EMPTY = "░";
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final OshiCpuProvider cpuProvider;
    private final OshiMemoryProvider memoryProvider;
    private final GpuProvider gpuProvider;
    private final DiskProvider diskProvider;
    private final ServiceProvider serviceProvider;
    private final HostProvider hostProvider;
    private final AtomicReference<DashboardSnapshot> snapshot = new AtomicReference<>();
    private final TimeSeriesBuffer cpuHistory = new TimeSeriesBuffer();
    private final TimeSeriesBuffer memoryHistory = new TimeSeriesBuffer();
    private final TimeSeriesBuffer swapHistory = new TimeSeriesBuffer();
    private final TimeSeriesBuffer temperatureHistory = new TimeSeriesBuffer();
    private final TimeSeriesBuffer gpuHistory = new TimeSeriesBuffer();
    private final TimeSeriesBuffer diskIoHistory = new TimeSeriesBuffer();
    private final AtomicReference<List<TimeSeriesBuffer>> coreHistories =
            new AtomicReference<>(List.of());
    private final Map<String, FilesystemHistory> filesystemHistories = new HashMap<>();
    private final MetricScheduler scheduler = new MetricScheduler();
    private final EventLogBuffer eventLog = new EventLogBuffer();
    private final EventTransitionTracker transitions = new EventTransitionTracker(eventLog);
    private final AtomicBoolean loading = new AtomicBoolean();
    private final AtomicInteger selectedService = new AtomicInteger();
    private final AtomicInteger selectedCard = new AtomicInteger();
    private final AtomicBoolean details = new AtomicBoolean();
    private final AtomicReference<TimeSeriesBuffer.Range> detailRange =
            new AtomicReference<>(TimeSeriesBuffer.Range.FIFTEEN_MINUTES);
    private final AtomicInteger detailCursor = new AtomicInteger(-1);
    private final AtomicBoolean filesystemWrite = new AtomicBoolean();
    private final AtomicBoolean perCore = new AtomicBoolean();
    private final AtomicInteger selectedCore = new AtomicInteger();
    private final AtomicReference<List<AtomicInteger>> coreCursors =
            new AtomicReference<>(List.of());
    private final AtomicBoolean explainer = new AtomicBoolean(true);
    private final AtomicLong generation = new AtomicLong();
    private Executor background;

    private boolean filteringEvents;
    private String eventFilterQuery = "";
    // sample() writes errors on a worker; render() reads them on the UI thread;
    // AtomicReference to make the updates visible.
    private final AtomicReference<String> pageError = new AtomicReference<>();
    // sample() writes service status on a worker; render() reads it on the UI thread;
    // so AtomicReference makes the updates visible.
    private final AtomicReference<String> serviceMessage = new AtomicReference<>("Loading services...");

    public DashboardPage(
            OshiCpuProvider cpuProvider,
            OshiMemoryProvider memoryProvider,
            GpuProvider gpuProvider,
            DiskProvider diskProvider,
            ServiceProvider serviceProvider,
            HostProvider hostProvider) {
        this.cpuProvider = cpuProvider;
        this.memoryProvider = memoryProvider;
        this.gpuProvider = gpuProvider;
        this.diskProvider = diskProvider;
        this.serviceProvider = serviceProvider;
        this.hostProvider = hostProvider;
    }

    private static TextColor rgb(int red, int green, int blue) {
        return new TextColor.RGB(red, green, blue);
    }

    @Override
    public String title() {
        return "System overview";
    }

    @Override
    public void onShow(Executor background) {
        this.background = background;
        generation.incrementAndGet();
        scheduler.clear();
        scheduler.register(new TimeSeriesBuffer(), Duration.ofSeconds(1), () -> {
            sample();
            return Double.NaN;
        });
        scheduler.register(new TimeSeriesBuffer(), Duration.ofSeconds(5), () -> {
            sampleGpu();
            return Double.NaN;
        });
        scheduler.register(new TimeSeriesBuffer(), Duration.ofSeconds(5), () -> {
            sampleServices();
            return Double.NaN;
        });
        scheduler.start();
        refresh();
    }

    @Override
    public void onHide() {
        generation.incrementAndGet();
        scheduler.stop();
        background = null;
    }

    @Override
    public List<String> helpLines() {
        if (details.get()) {
            OverviewCard card = currentCard();
            if (card.kind() == OverviewCard.Kind.CPU) {
                return List.of(
                        "  ←/→           Switch between KPI drill-ins",
                        "  h/l           Move inspection cursor left / right",
                        "  [ / ] or t    Change time range (15M / 1H / 6H)",
                        "  Tab           Toggle Total CPU / Per-core view",
                        "  ↑/↓ or j/k    Select core (when in per-core mode)",
                        "  e             Toggle metric explanation box",
                        "  Esc / Enter   Close detail overlay"
                );
            }
            if (card.filesystem()) {
                return List.of(
                        "  ←/→           Switch between KPI drill-ins",
                        "  h/l           Move inspection cursor left / right",
                        "  [ / ] or t    Change time range (15M / 1H / 6H)",
                        "  Tab or ↑/↓    Toggle Read / Write I/O series",
                        "  e             Toggle metric explanation box",
                        "  Esc / Enter   Close detail overlay"
                );
            }
            return List.of(
                    "  ←/→           Switch between KPI drill-ins",
                    "  h/l           Move inspection cursor left / right",
                    "  [ / ] or t    Change time range (15M / 1H / 6H)",
                    "  e             Toggle metric explanation box",
                    "  Esc / Enter   Close detail overlay"
            );
        }
        if (filteringEvents) {
            return List.of(
                    "  Type query    Filter events by message or severity",
                    "  Enter         Apply current event filter",
                    "  Esc           Cancel filter and show all events",
                    "  Backspace     Delete character"
            );
        }
        return List.of(
                "  ←/→ or h/l    Select KPI card",
                "  Enter         Open detail overlay for selected KPI",
                "  /             Filter events in event log",
                "  r             Refresh metrics now",
                "  1..2 / Tab    Switch to another page"
        );
    }

    @Override
    public void handleKey(KeyStroke key) {
        if (filteringEvents) {
            handleEventFilterKey(key);
            return;
        }

        OverviewCard card = currentCard();
        if (key.getKeyType() == KeyType.Escape) {
            if (details.get()) {
                details.set(false);
            } else if (!eventFilterQuery.isEmpty()) {
                eventFilterQuery = "";
            }
        } else if (key.getKeyType() == KeyType.Enter) {
            if (details.get()) {
                details.set(false);
            } else {
                details.set(true);
                resetDetailState();
            }
        } else if (details.get() && card.kind() == OverviewCard.Kind.CPU
                && key.getKeyType() == KeyType.Tab) {
            perCore.set(!perCore.get());
            selectedCore.set(Math.max(0, Math.min(selectedCore.get(), coreHistories.get().size() - 1)));
        } else if (details.get() && card.kind() == OverviewCard.Kind.CPU && perCore.get()
                && isNextCoreKey(key)) {
            selectedCore.updateAndGet(value -> Math.min(value + 1, Math.max(0, coreHistories.get().size() - 1)));
        } else if (details.get() && card.kind() == OverviewCard.Kind.CPU && perCore.get()
                && isPreviousCoreKey(key)) {
            selectedCore.updateAndGet(value -> Math.max(0, value - 1));
        } else if (details.get() && card.filesystem()
                && key.getKeyType() == KeyType.Tab) {
            filesystemWrite.set(!filesystemWrite.get());
        } else if (details.get() && card.filesystem() && isNextCoreKey(key)) {
            filesystemWrite.set(true);
        } else if (details.get() && card.filesystem() && isPreviousCoreKey(key)) {
            filesystemWrite.set(false);
        } else if (details.get() && key.getKeyType() == KeyType.ArrowRight) {
            moveDrillIn(1);
        } else if (details.get() && key.getKeyType() == KeyType.ArrowLeft) {
            moveDrillIn(-1);
        } else if (details.get() && isNextRangeKey(key)) {
            detailRange.updateAndGet(TimeSeriesBuffer.Range::next);
            resetCursor();
        } else if (details.get() && isPreviousRangeKey(key)) {
            detailRange.updateAndGet(TimeSeriesBuffer.Range::previous);
            resetCursor();
        } else if (details.get() && isNextCursorKey(key)) {
            moveCursor(1);
        } else if (details.get() && isPreviousCursorKey(key)) {
            moveCursor(-1);
        } else if (details.get() && key.getKeyType() == KeyType.Character && key.getCharacter() != null
                && Character.toLowerCase(key.getCharacter()) == 'e') {
            explainer.set(!explainer.get());
        } else if (!details.get() && isNextCardKey(key)) {
            selectedCard.updateAndGet(value -> Math.min(value + 1, Math.max(0, cardCount() - 1)));
        } else if (!details.get() && isPreviousCardKey(key)) {
            selectedCard.updateAndGet(value -> Math.max(0, value - 1));
        } else if (key.getKeyType() == KeyType.Character && key.getCharacter() != null) {
            char ch = Character.toLowerCase(key.getCharacter());
            if (ch == 'r') {
                refresh();
            } else if (ch == '/' && !details.get()) {
                filteringEvents = true;
            }
        }
    }

    private void handleEventFilterKey(KeyStroke key) {
        if (key.getKeyType() == KeyType.Enter) {
            filteringEvents = false;
            return;
        }
        if (key.getKeyType() == KeyType.Escape) {
            filteringEvents = false;
            eventFilterQuery = "";
            return;
        }
        if (key.getKeyType() == KeyType.Backspace) {
            if (!eventFilterQuery.isEmpty()) {
                eventFilterQuery = eventFilterQuery.substring(0, eventFilterQuery.length() - 1);
            }
            return;
        }
        Character c = key.getCharacter();
        if (c != null && !Character.isISOControl(c)) {
            eventFilterQuery += c;
        }
    }

    private void resetDetailState() {
        detailRange.set(TimeSeriesBuffer.Range.FIFTEEN_MINUTES);
        perCore.set(false);
        selectedCore.set(0);
        filesystemWrite.set(false);
        explainer.set(true);
        resetCursor();
    }

    /** Moves to the previous/next drill-in (KPI card) while an overlay is open. */
    private void moveDrillIn(int delta) {
        int count = cardCount();
        if (count == 0) {
            return;
        }
        int next = Math.max(0, Math.min(count - 1, selectedCard.get() + delta));
        if (next == selectedCard.get()) {
            return;
        }
        selectedCard.set(next);
        perCore.set(false);
        selectedCore.set(0);
        filesystemWrite.set(false);
        resetCursor();
    }

    private void resetCursor() {
        if (perCore.get()) {
            AtomicInteger cursor = selectedCoreCursor();
            if (cursor != null) cursor.set(-1);
        } else {
            detailCursor.set(-1);
        }
    }

    private void moveCursor(int delta) {
        OverviewCard card = currentCard();
        boolean perCoreCard = card.kind() == OverviewCard.Kind.CPU && perCore.get();
        TimeSeriesBuffer history = perCoreCard ? selectedCoreHistory() : historyForSelectedCard();
        if (history == null) {
            return;
        }
        int length = history.samples(detailRange.get()).length;
        if (length == 0) {
            return;
        }
        AtomicInteger cursorState = perCoreCard ? selectedCoreCursor() : detailCursor;
        if (cursorState == null) return;
        int current = cursorState.get();
        if (current < 0) {
            if (delta > 0) {
                return;
            }
            current = length - 1;
        } else if (delta > 0 && current == length - 1) {
            cursorState.set(-1);
            return;
        } else {
            current = Math.max(0, Math.min(length - 1, current + delta));
        }
        cursorState.set(current);
    }

    private AtomicInteger selectedCoreCursor() {
        List<AtomicInteger> cursors = coreCursors.get();
        int index = selectedCore.get();
        return index >= 0 && index < cursors.size() ? cursors.get(index) : null;
    }

    private TimeSeriesBuffer selectedCoreHistory() {
        List<TimeSeriesBuffer> histories = coreHistories.get();
        int index = selectedCore.get();
        return index >= 0 && index < histories.size() ? histories.get(index) : null;
    }

    private TimeSeriesBuffer historyForSelectedCard() {
        return switch (currentCard().kind()) {
            case CPU -> cpuHistory;
            case RAM -> memoryHistory;
            case TEMPERATURE -> temperatureHistory;
            case GPU -> gpuHistory;
            case FILESYSTEM -> {
                FilesystemHistory history = filesystemHistory(currentCard().mountPoint());
                yield filesystemWrite.get() ? history.write() : history.read();
            }
            case DISK_TOTAL -> diskIoHistory;
        };
    }

    private OverviewCard currentCard() {
        List<OverviewCard> cards = OverviewCard.cards(snapshot.get());
        int index = Math.max(0, Math.min(selectedCard.get(), cards.size() - 1));
        return cards.get(index);
    }

    private int cardCount() {
        return OverviewCard.cards(snapshot.get()).size();
    }

    private static boolean isNextCoreKey(KeyStroke key) {
        return key.getKeyType() == KeyType.ArrowDown
                || key.getKeyType() == KeyType.Character && key.getCharacter() != null
                && Character.toLowerCase(key.getCharacter()) == 'j';
    }

    private static boolean isPreviousCoreKey(KeyStroke key) {
        return key.getKeyType() == KeyType.ArrowUp
                || key.getKeyType() == KeyType.Character && key.getCharacter() != null
                && Character.toLowerCase(key.getCharacter()) == 'k';
    }

    @Override
    public void refresh() {
        Executor executor = background;
        if (executor == null || !loading.compareAndSet(false, true)) {
            return;
        }
        try {
            executor.execute(() -> {
                try {
                    sample();
                    sampleGpu();
                    sampleServices();
                } finally {
                    loading.set(false);
                }
            });
        } catch (RejectedExecutionException e) {
            loading.set(false);
            pageError.set("Refresh not started: " + message(e));
        }
    }

    private void sample() {
        CpuSnapshot cpu = null;
        MemorySnapshot memory = null;
        DiskSnapshot disk = null;
        Map<String, String> errors = new LinkedHashMap<>();
        try {
            cpu = cpuProvider.read();
        } catch (RuntimeException e) {
            errors.put("CPU", message(e));
            recordProviderError("CPU", message(e));
        }
        try {
            memory = memoryProvider.read();
        } catch (RuntimeException e) {
            errors.put("RAM", message(e));
            recordProviderError("RAM", message(e));
        }
        try {
            disk = diskProvider.read();
        } catch (IOException | RuntimeException e) {
            errors.put("Disks", message(e));
            recordProviderError("Disks", message(e));
        }
        Double cpuUsage = cpu == null ? null : cpu.totalUsagePercent();
        cpuHistory.append(cpuUsage == null ? Double.NaN : cpuUsage);
        appendCoreHistories(cpu);
        temperatureHistory.append(cpu == null || cpu.temperatureCelsius() == null
                ? Double.NaN : cpu.temperatureCelsius());
        memoryHistory.append(memory == null
                ? Double.NaN
                : ratio(memory.usedBytes(), memory.totalBytes()));
        swapHistory.append(memory == null || memory.swapTotalBytes() <= 0
                ? Double.NaN : ratio(memory.swapUsedBytes(), memory.swapTotalBytes()));
        diskIoHistory.append(totalFilesystemIo(disk));
        if (disk != null) {
            for (DiskSnapshot.Filesystem fs : disk.filesystems()) {
                FilesystemHistory history = filesystemHistory(fs.mountPoint());
                history.read().append(mib(fs.readBytesPerSecond()));
                history.write().append(mib(fs.writeBytesPerSecond()));
                filesystemEvent(fs);
            }
        }
        updateSnapshot(cpu, memory, null, disk, null, errors, readHost());
    }

    /** Reads the header identity; a failure keeps the previous values instead of failing the page. */
    private HostSnapshot readHost() {
        if (hostProvider == null) {
            return snapshot.get() == null ? null : snapshot.get().host();
        }
        try {
            return hostProvider.read();
        } catch (RuntimeException ignored) {
            // Host identity is cosmetic: keep the last good values.
            return snapshot.get() == null ? null : snapshot.get().host();
        }
    }

    private void appendCoreHistories(CpuSnapshot cpu) {
        int coreCount = cpu == null ? coreHistories.get().size() : cpu.cores().size();
        ensureCoreHistories(coreCount);
        List<TimeSeriesBuffer> histories = coreHistories.get();
        for (int index = 0; index < histories.size(); index++) {
            Double usage = cpu == null || index >= cpu.cores().size()
                    ? null : cpu.cores().get(index).usagePercent();
            histories.get(index).append(usage == null ? Double.NaN : usage);
        }
    }

    private void ensureCoreHistories(int coreCount) {
        List<TimeSeriesBuffer> current = coreHistories.get();
        if (current.size() == coreCount) return;
        List<TimeSeriesBuffer> next = new java.util.ArrayList<>();
        List<AtomicInteger> nextCursors = new java.util.ArrayList<>();
        for (int index = 0; index < coreCount; index++) {
            next.add(index < current.size() ? current.get(index) : new TimeSeriesBuffer());
            List<AtomicInteger> cursors = coreCursors.get();
            nextCursors.add(index < cursors.size() ? cursors.get(index) : new AtomicInteger(-1));
        }
        coreHistories.set(List.copyOf(next));
        coreCursors.set(List.copyOf(nextCursors));
        selectedCore.updateAndGet(value -> Math.max(0, Math.min(value, Math.max(0, coreCount - 1))));
    }

    private void sampleGpu() {
        try {
            GpuReading gpu = gpuProvider.read();
            Double utilization = firstGpuUtilization(gpu);
            gpuHistory.append(utilization == null ? Double.NaN : utilization);
            Map<String, String> errors = new LinkedHashMap<>();
            if (!gpu.errors().isEmpty()) {
                errors.put("GPU", String.join("; ", gpu.errors()));
                recordProviderError("GPU", errors.get("GPU"));
            }
            updateSnapshot(null, null, gpu, null, null, errors);
            for (GpuSnapshot device : gpu.devices()) {
                thresholdEvent("temperature:" + device.name(), device.temperatureCelsius(), 80, 90, "GPU temperature");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            gpuHistory.append(Double.NaN);
            recordProviderError("GPU", "Read interrupted");
        } catch (RuntimeException e) {
            gpuHistory.append(Double.NaN);
            recordProviderError("GPU", message(e));
        }
    }

    private void sampleServices() {
        try {
            List<ServiceSnapshot> services = serviceProvider.list();
            serviceMessage.set(services.size() + " services loaded.");
            selectedService.set(Math.max(0, Math.min(selectedService.get(), services.size() - 1)));
            for (ServiceSnapshot service : services) {
                transition("service:" + service.unit(), service.active().equalsIgnoreCase("failed") ? "failed" : "ok",
                        "Service " + service.unit() + " failed", EventLogBuffer.Severity.CRITICAL);
            }
            updateSnapshot(null, null, null, null, services, Map.of());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            recordProviderError("Services", "Read interrupted");
        } catch (IOException | RuntimeException e) {
            recordProviderError("Services", message(e));
            serviceMessage.set("Error: " + message(e));
        }
    }

    void updateSnapshot(CpuSnapshot cpu, MemorySnapshot memory, GpuReading gpu,
            DiskSnapshot disk, List<ServiceSnapshot> services, Map<String, String> errors) {
        updateSnapshot(cpu, memory, gpu, disk, services, errors, null);
    }

    void updateSnapshot(CpuSnapshot cpu, MemorySnapshot memory, GpuReading gpu,
            DiskSnapshot disk, List<ServiceSnapshot> services, Map<String, String> errors,
            HostSnapshot host) {
        DashboardSnapshot old = snapshot.get();
        snapshot.set(new DashboardSnapshot(Instant.now(), cpu != null ? cpu : old == null ? null : old.cpu(),
                memory != null ? memory : old == null ? null : old.memory(), gpu != null ? gpu : old == null ? null : old.gpu(),
                disk != null ? disk : old == null ? null : old.disk(), services != null ? services : old == null ? List.of() : old.services(),
                errors.isEmpty() && old != null ? old.errors() : errors,
                host != null ? host : old == null ? null : old.host()));
        pageError.set(errors.isEmpty() ? null : errors.values().iterator().next());
    }

    private FilesystemHistory filesystemHistory(String mount) {
        synchronized (filesystemHistories) {
            return filesystemHistories.computeIfAbsent(mount, ignored ->
                    new FilesystemHistory(new TimeSeriesBuffer(), new TimeSeriesBuffer()));
        }
    }

    public EventLogBuffer eventLog() {
        return eventLog;
    }

    int selectedCard() {
        return selectedCard.get();
    }

    boolean showingDetails() {
        return details.get();
    }

    @Override
    public boolean capturesGlobalNavigation() {
        return details.get() || filteringEvents;
    }

    TimeSeriesBuffer.Range detailRange() {
        return detailRange.get();
    }

    boolean perCoreSelected() {
        return perCore.get();
    }

    int detailCursor() {
        boolean perCoreCard = perCore.get()
                && currentCard().kind() == OverviewCard.Kind.CPU;
        AtomicInteger cursor = perCoreCard ? selectedCoreCursor() : detailCursor;
        return cursor == null ? -1 : cursor.get();
    }

    int selectedCore() {
        return selectedCore.get();
    }

    boolean filesystemWriteSelected() {
        return filesystemWrite.get();
    }

    @Override
    public void close() {
        scheduler.close();
    }

    private void filesystemEvent(DiskSnapshot.Filesystem filesystem) {
        Double value = filesystem.utilizationPercent();
        String state = value == null ? "unknown" : value >= 95 ? "critical" : value >= 85 ? "warning" : "ok";
        transition("filesystem:" + filesystem.mountPoint(), state,
                "Filesystem " + filesystem.mountPoint() + " above threshold (" + state + ")",
                "critical".equals(state) ? EventLogBuffer.Severity.CRITICAL : EventLogBuffer.Severity.WARM);
    }

    private void thresholdEvent(String key, Double value, double warning, double critical, String label) {
        String state = value == null ? "unknown" : value >= critical ? "critical" : value >= warning ? "warning" : "ok";
        transition(key, state, label + " above threshold (" + state + ")",
                "critical".equals(state) ? EventLogBuffer.Severity.CRITICAL : EventLogBuffer.Severity.WARM);
    }

    private void recordProviderError(String provider, String detail) {
        transition("provider:" + provider, "error", provider + " provider error: " + detail,
                EventLogBuffer.Severity.CRITICAL);
    }

    private void transition(String key, String state, String message, EventLogBuffer.Severity severity) {
        transitions.transition(key, state, severity, message);
    }

    private static double totalFilesystemIo(DiskSnapshot disk) {
        if (disk == null) {
            return Double.NaN;
        }
        double total = 0;
        boolean available = false;
        for (DiskSnapshot.Filesystem filesystem : disk.filesystems()) {
            double value = combinedMib(filesystem);
            if (!Double.isNaN(value)) {
                total += value;
                available = true;
            }
        }
        return available ? total : Double.NaN;
    }

    private static double combinedMib(DiskSnapshot.Filesystem filesystem) {
        boolean hasRead = filesystem.readBytesPerSecond() != null;
        boolean hasWrite = filesystem.writeBytesPerSecond() != null;
        if (!hasRead && !hasWrite) {
            return Double.NaN;
        }
        double bytes = (hasRead ? filesystem.readBytesPerSecond() : 0)
                + (hasWrite ? filesystem.writeBytesPerSecond() : 0);
        return bytes / (1024.0 * 1024);
    }

    private static double mib(Double bytesPerSecond) {
        return bytesPerSecond == null
                ? Double.NaN : bytesPerSecond / (1024.0 * 1024);
    }

    private static Double firstGpuUtilization(GpuReading gpu) {
        return gpu.devices().isEmpty() ? null : gpu.devices().get(0).utilizationPercent();
    }

    @Override
    public void render(TextGraphics graphics, TerminalSize size) {
        DashboardOverviewRenderer.draw(graphics, size, snapshot.get(), cpuHistory, memoryHistory,
                swapHistory, temperatureHistory, gpuHistory, diskIoHistory,
                eventLog, pageError.get(), selectedCard.get(), details.get(), detailRange.get(),
                detailCursor(), perCore.get(), explainer.get(), coreHistories.get(), selectedCore.get(),
                coreCursors.get().stream().mapToInt(AtomicInteger::get).toArray(),
                filesystemSeries(), filesystemWrite.get(), filteringEvents, eventFilterQuery);
    }

    private List<FilesystemSeries> filesystemSeries() {
        DashboardSnapshot current = snapshot.get();
        if (current == null || current.disk() == null) {
            return List.of();
        }
        List<FilesystemSeries> series = new java.util.ArrayList<>();
        for (DiskSnapshot.Filesystem filesystem : current.disk().filesystems()) {
            FilesystemHistory history = filesystemHistory(filesystem.mountPoint());
            series.add(FilesystemSeries.from(filesystem, history.read(), history.write()));
        }
        return List.copyOf(series);
    }

    private static boolean isNextCardKey(KeyStroke key) {
        if (key.getKeyType() == KeyType.ArrowRight || key.getKeyType() == KeyType.ArrowDown) {
            return true;
        }
        return key.getKeyType() == KeyType.Character && key.getCharacter() != null
                && Character.toLowerCase(key.getCharacter()) == 'l';
    }

    private static boolean isPreviousCardKey(KeyStroke key) {
        if (key.getKeyType() == KeyType.ArrowLeft || key.getKeyType() == KeyType.ArrowUp) {
            return true;
        }
        return key.getKeyType() == KeyType.Character && key.getCharacter() != null
                && Character.toLowerCase(key.getCharacter()) == 'h';
    }

    private static boolean isNextRangeKey(KeyStroke key) {
        return key.getKeyType() == KeyType.Character && key.getCharacter() != null
                && (key.getCharacter() == ']' || Character.toLowerCase(key.getCharacter()) == 't');
    }

    private static boolean isPreviousRangeKey(KeyStroke key) {
        return key.getKeyType() == KeyType.Character && key.getCharacter() != null
                && key.getCharacter() == '[';
    }

    private static boolean isNextCursorKey(KeyStroke key) {
        return key.getKeyType() == KeyType.Character && key.getCharacter() != null
                && Character.toLowerCase(key.getCharacter()) == 'l';
    }

    private static boolean isPreviousCursorKey(KeyStroke key) {
        return key.getKeyType() == KeyType.Character && key.getCharacter() != null
                && Character.toLowerCase(key.getCharacter()) == 'h';
    }

    void drawHeader(TextGraphics graphics, int width) {
        graphics.setBackgroundColor(BACKGROUND);
        put(graphics, 1, 0, width - 2, " THYME  /  SYSTEM MONITOR ", TEAL, true);
        put(graphics, Math.max(30, width - 22), 0, 20,
                snapshot.get() == null ? "LIVE  •  --:--:--" : "LIVE  •  " + CLOCK.format(snapshot.get().sampledAt()),
                MUTED);
        graphics.setForegroundColor(BORDER);
        if (width > 0) {
            graphics.drawLine(0, 1, width - 1, 1, '─');
        }
    }

    void drawFooter(TextGraphics graphics, int width, int height) {
        graphics.setBackgroundColor(BACKGROUND);
        graphics.setForegroundColor(BORDER);
        graphics.drawLine(0, height - 1, width - 1, height - 1, '─');
        put(graphics, 1, height - 1, width - 2,
                " d containers   •   ↑/↓ select service   •   r refresh   •   q quit ", MUTED);
    }

    static void drawPanel(
            TextGraphics graphics, int x, int y, int width, int height, String title, TextColor accent) {
        if (width < 4 || height < 3) {
            return;
        }
        graphics.setBackgroundColor(PANEL);
        graphics.fillRectangle(new TerminalPosition(x, y), new TerminalSize(width, height), ' ');
        graphics.setForegroundColor(BORDER);
        graphics.drawLine(x, y, x + width - 1, y, '─');
        graphics.drawLine(x, y + height - 1, x + width - 1, y + height - 1, '─');
        graphics.drawLine(x, y, x, y + height - 1, '│');
        graphics.drawLine(x + width - 1, y, x + width - 1, y + height - 1, '│');
        graphics.setCharacter(x, y, '┌');
        graphics.setCharacter(x + width - 1, y, '┐');
        graphics.setCharacter(x, y + height - 1, '└');
        graphics.setCharacter(x + width - 1, y + height - 1, '┘');
        put(graphics, x + 2, y, width - 4, " " + title + " ", accent, true);
    }

    void drawCpu(TextGraphics graphics, DashboardSnapshot current, int x, int y, int width, int height) {
        CpuSnapshot cpu = current.cpu();
        int row = y + 1;
        if (cpu == null) {
            put(graphics, x + 2, row, width - 4, "Unavailable: " + current.errors().getOrDefault("CPU", "no data"), RED);
            return;
        }
        int bottom = y + height - 1;
        if (row >= bottom) {
            return;
        }
        put(graphics, x + 2, row, width - 4, shorten(cpu.model(), width - 5), TEXT);
        row++;
        if (row < bottom) {
            row = gauge(graphics, x + 2, row, width - 4, "TOTAL", cpu.totalUsagePercent(), TEAL);
        }
        if (row < bottom) {
            put(graphics, x + 2, row, width - 4,
                    "15m " + sparkline(cpuHistory.samples(TimeSeriesBuffer.Range.FIFTEEN_MINUTES), width - 18)
                    + age(cpuHistory), TEAL);
            row++;
        }
        if (row < bottom) {
            put(graphics, x + 2, row, width - 4,
                    "Temp " + temperature(cpu.temperatureCelsius()) + "   "
                    + cpu.logicalProcessors() + " threads",
                    MUTED);
            row++;
        }
        int coresPerLine = Math.max(1, (width - 5) / 12);
        for (int index = 0; index < cpu.cores().size() && row < bottom; index += coresPerLine) {
            StringBuilder line = new StringBuilder();
            for (int core = index; core < Math.min(index + coresPerLine, cpu.cores().size()); core++) {
                if (!line.isEmpty()) {
                    line.append("  ");
                }
                CpuSnapshot.Core value = cpu.cores().get(core);
                line.append("C").append(value.index()).append(' ')
                        .append(percent(value.usagePercent())).append(' ')
                        .append(shortFrequency(value.frequencyHz()));
            }
            put(graphics, x + 2, row, width - 4, line.toString(), BLUE);
            row++;
        }
    }

    void drawMemory(TextGraphics graphics, DashboardSnapshot current, int x, int y, int width, int height) {
        MemorySnapshot memory = current.memory();
        if (memory == null) {
            put(graphics, x + 2, y + 1, width - 4,
                    "Unavailable: " + current.errors().getOrDefault("RAM", "no data"), RED);
            return;
        }
        int bottom = y + height - 1;
        int row = gauge(graphics, x + 2, y + 1, width - 4, "RAM", ratio(memory.usedBytes(), memory.totalBytes()),
                BLUE);
        if (row < bottom) {
            put(graphics, x + 2, row, width - 4,
                    gib(memory.usedBytes()) + " / " + gib(memory.totalBytes()) + " GiB used",
                    TEXT);
            row++;
        }
        if (row < y + height - 1) {
            put(graphics, x + 2, row, width - 4,
                    "15m " + sparkline(memoryHistory.samples(TimeSeriesBuffer.Range.FIFTEEN_MINUTES), width - 18)
                    + age(memoryHistory), BLUE);
            row++;
        }
        if (row < y + height - 1) {
            row = gauge(graphics, x + 2, row, width - 4, "SWAP",
                    ratio(memory.swapUsedBytes(), memory.swapTotalBytes()), PURPLE);
        }
        if (row < y + height - 1) {
            put(graphics, x + 2, row, width - 4,
                    gib(memory.swapUsedBytes()) + " / " + gib(memory.swapTotalBytes()) + " GiB used", MUTED);
        }
    }

    void drawGpu(TextGraphics graphics, DashboardSnapshot current, int x, int y, int width, int height) {
        GpuReading gpu = current.gpu();
        if (gpu == null || gpu.devices().isEmpty()) {
            put(graphics, x + 2, y + 1, width - 4, "No graphics device detected", MUTED);
            if (gpu != null && !gpu.errors().isEmpty()) {
                put(graphics, x + 2, y + 2, width - 4, shorten(String.join("; ", gpu.errors()), width - 5), ORANGE);
            }
            return;
        }
        int row = y + 1;
        for (GpuSnapshot device : gpu.devices()) {
            if (row >= y + height - 1) {
                break;
            }
            put(graphics, x + 2, row, width - 4,
                    shorten(device.vendor() + "  " + device.name(), width - 5), TEXT);
            row++;
            if (row < y + height - 1) {
                row = gauge(graphics, x + 2, row, width - 4, "GPU", device.utilizationPercent(), PURPLE);
            }
            if (row < y + height - 1) {
                put(graphics, x + 2, row, width - 4,
                        "VRAM " + memory(device.memoryUsedBytes()) + " / " + memory(device.memoryTotalBytes())
                        + "   " + temperature(device.temperatureCelsius()),
                        MUTED);
                row++;
            }
            if (row < y + height - 1) {
                put(graphics, x + 2, row, width - 4,
                        "P " + watts(device.powerWatts()) + "/" + watts(device.powerLimitWatts())
                        + "   C " + mhz(device.graphicsClockMhz())
                        + "   M " + mhz(device.memoryClockMhz()),
                        MUTED);
                row++;
            }
        }
        String error = current.errors().get("GPU");
        if (error != null && row < y + height - 1) {
            put(graphics, x + 2, row, width - 4, "Partial metrics: " + error, ORANGE);
        }
    }

    void drawServices(
            TextGraphics graphics, DashboardSnapshot current, int x, int y, int width, int height) {
        List<ServiceSnapshot> services = current.services();
        int usableRows = Math.max(0, height - 3);
        if (services.isEmpty()) {
            put(graphics, x + 2, y + 1, width - 4, serviceMessage.get(), MUTED);
            return;
        }
        int visible = Math.min(services.size(), usableRows);
        int start = Math.max(0, Math.min(selectedService.get() - visible + 1, services.size() - visible));
        for (int row = 0; row < visible; row++) {
            int index = start + row;
            ServiceSnapshot service = services.get(index);
            boolean selected = index == selectedService.get();
            String state = service.active().equalsIgnoreCase("active") ? "●" : "○";
            String line = state + " " + shorten(service.unit(), Math.max(8, width / 2))
                    + "  " + service.active() + "/" + service.sub();
            put(graphics, x + 2, y + 1 + row, width - 4, line, selected ? TEAL : serviceColor(service));
        }
        if (height > 3) {
            put(graphics, x + 2, y + height - 2, width - 4, serviceMessage.get(), MUTED);
        }
    }

    private static int gauge(
            TextGraphics graphics, int x, int y, int width, String label, Double value, TextColor accent) {
        if (width < 10) {
            put(graphics, x, y, width, label + " " + percent(value), accent);
            return y + 1;
        }
        String valueText = percent(value);
        int barWidth = Math.max(4, width - label.length() - valueText.length() - 4);
        put(graphics, x, y, label.length(), label, MUTED, true);
        int barX = x + label.length() + 1;
        int filled = value == null ? 0 : (int) Math.round(barWidth * Math.max(0, Math.min(100, value)) / 100);
        put(graphics, barX, y, filled, BAR_FULL.repeat(filled), accent);
        put(graphics, barX + filled, y, barWidth - filled, BAR_EMPTY.repeat(barWidth - filled), BORDER);
        put(graphics, barX + barWidth + 1, y, valueText.length(), valueText, accent, true);
        return y + 1;
    }

    private static TextColor serviceColor(ServiceSnapshot service) {
        return service.active().equalsIgnoreCase("active") ? GREEN : MUTED;
    }

    private static void put(TextGraphics graphics, int x, int y, int width, String text, TextColor color) {
        put(graphics, x, y, width, text, color, false);
    }

    private static void put(
            TextGraphics graphics, int x, int y, int width, String text, TextColor color, boolean bold) {
        if (width <= 0 || text == null) {
            return;
        }
        String clipped = text.substring(0, Math.min(text.length(), width));
        graphics.setForegroundColor(color);
        if (bold) {
            graphics.putString(x, y, clipped, com.googlecode.lanterna.SGR.BOLD);
        } else {
            graphics.putString(x, y, clipped);
        }
    }

    private static String message(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    private static String shorten(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return maxLength <= 1 ? value.substring(0, maxLength) : value.substring(0, maxLength - 1) + "…";
    }

    private static double ratio(long used, long total) {
        return total == 0 ? 0.0 : Math.min(100.0, used * 100.0 / total);
    }

    private static String percent(Double value) {
        return value == null ? "n/a" : String.format("%.0f%%", value);
    }

    static String decimal(Double value) {
        return value == null ? "n/a" : String.format("%.2f", value);
    }

    private static String gib(long bytes) {
        return String.format("%.1f", bytes / (1024.0 * 1024 * 1024));
    }

    private static String memory(Long bytes) {
        return bytes == null ? "n/a" : String.format("%.1fG", bytes / (1024.0 * 1024 * 1024));
    }

    private static String temperature(Double celsius) {
        return celsius == null ? "n/a" : String.format("%.0f°C", celsius);
    }

    private static String watts(Double value) {
        return value == null ? "n/a" : String.format("%.0fW", value);
    }

    private static String mhz(Long value) {
        return value == null ? "n/a" : value + "MHz";
    }

    private static String shortFrequency(Long hertz) {
        return hertz == null ? "n/a" : String.format("%.1fG", hertz / 1_000_000_000.0);
    }

    private static String age(TimeSeriesBuffer buffer) {
        Duration age = buffer.latestAge();
        if (age == null || age.compareTo(Duration.ofSeconds(2)) <= 0) {
            return "";
        }
        return "  age " + Math.max(1, age.toSeconds()) + "s";
    }

    private static String sparkline(double[] values, int width) {
        if (values.length == 0 || width <= 0) {
            return "waiting for samples";
        }
        int start = Math.max(0, values.length - width);
        StringBuilder line = new StringBuilder(values.length - start);
        for (int index = start; index < values.length; index++) {
            if (Double.isNaN(values[index])) {
                line.append(' ');
                continue;
            }
            double value = Math.max(0, Math.min(100, values[index]));
            line.append(SPARKLINE[(int) Math.round(value / 100 * (SPARKLINE.length - 1))]);
        }
        return line.toString();
    }

    private record FilesystemHistory(TimeSeriesBuffer read, TimeSeriesBuffer write) {
    }
}
