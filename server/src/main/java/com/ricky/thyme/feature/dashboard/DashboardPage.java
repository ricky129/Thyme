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
import java.time.Instant;
import java.time.Duration;
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
    private final AtomicReference<DashboardSnapshot> snapshot = new AtomicReference<>();
    private final TimeSeriesBuffer cpuHistory = new TimeSeriesBuffer();
    private final TimeSeriesBuffer memoryHistory = new TimeSeriesBuffer();
    private final Map<String, TimeSeriesBuffer> filesystemHistory = new HashMap<>();
    private final MetricScheduler scheduler = new MetricScheduler();
    private final EventLogBuffer eventLog = new EventLogBuffer();
    private final EventTransitionTracker transitions = new EventTransitionTracker(eventLog);
    private final AtomicBoolean loading = new AtomicBoolean();
    private final AtomicInteger selectedService = new AtomicInteger();
    private final AtomicLong generation = new AtomicLong();
    private Executor background;
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
            ServiceProvider serviceProvider) {
        this.cpuProvider = cpuProvider;
        this.memoryProvider = memoryProvider;
        this.gpuProvider = gpuProvider;
        this.diskProvider = diskProvider;
        this.serviceProvider = serviceProvider;
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
        scheduler.register(new TimeSeriesBuffer(), Duration.ofSeconds(1), () -> { sampleFast(); return Double.NaN; });
        scheduler.register(new TimeSeriesBuffer(), Duration.ofSeconds(5), () -> { sampleGpu(); return Double.NaN; });
        scheduler.register(new TimeSeriesBuffer(), Duration.ofSeconds(5), () -> { sampleServices(); return Double.NaN; });
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
    public void handleKey(KeyStroke key) {
        if (key.getKeyType() == KeyType.ArrowDown)
            selectedService.updateAndGet(value -> Math.min(value + 1, Math.max(0, serviceCount() - 1)));
        else if (key.getKeyType() == KeyType.ArrowUp)
            selectedService.updateAndGet(value -> Math.max(0, value - 1));
        else if (key.getKeyType() == KeyType.Character && key.getCharacter() != null) {
            if (Character.toLowerCase(key.getCharacter()) == 'r')
                refresh();
        }
    }

    @Override
    public void refresh() {
        Executor executor = background;
        if (executor == null || !loading.compareAndSet(false, true)) return;
        try {
            executor.execute(() -> {
                try { sampleFast(); sampleGpu(); sampleServices(); }
                finally { loading.set(false); }
            });
        } catch (RejectedExecutionException e) {
            loading.set(false);
            pageError.set("Refresh not started: " + message(e));
        }
    }

    private void sampleFast() {
        CpuSnapshot cpu = null;
        MemorySnapshot memory = null;
        DiskSnapshot disk = null;
        Map<String, String> errors = new LinkedHashMap<>();
        try { cpu = cpuProvider.read(); }
        catch (RuntimeException e) { errors.put("CPU", message(e)); recordProviderError("CPU", message(e)); }
        try { memory = memoryProvider.read(); }
        catch (RuntimeException e) { errors.put("RAM", message(e)); recordProviderError("RAM", message(e)); }
        try { disk = diskProvider.read(); }
        catch (IOException | RuntimeException e) { errors.put("Disks", message(e)); recordProviderError("Disks", message(e)); }
        cpuHistory.append(cpu == null || cpu.totalUsagePercent() == null ? Double.NaN : cpu.totalUsagePercent());
        memoryHistory.append(memory == null ? Double.NaN : ratio(memory.usedBytes(), memory.totalBytes()));
        if (disk != null) for (DiskSnapshot.Filesystem fs : disk.filesystems()) {
            filesystemBuffer(fs.mountPoint()).append(fs.utilizationPercent() == null ? Double.NaN : fs.utilizationPercent());
            filesystemEvent(fs);
        }
        updateSnapshot(cpu, memory, null, disk, null, errors);
    }

    private void sampleGpu() {
        try {
            GpuReading gpu = gpuProvider.read();
            Map<String, String> errors = new LinkedHashMap<>();
            if (!gpu.errors().isEmpty()) { errors.put("GPU", String.join("; ", gpu.errors())); recordProviderError("GPU", errors.get("GPU")); }
            updateSnapshot(null, null, gpu, null, null, errors);
            for (GpuSnapshot device : gpu.devices())
                thresholdEvent("temperature:" + device.name(), device.temperatureCelsius(), 80, 90, "GPU temperature");
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); recordProviderError("GPU", "Read interrupted"); }
        catch (RuntimeException e) { recordProviderError("GPU", message(e)); }
    }

    private void sampleServices() {
        try {
            List<ServiceSnapshot> services = serviceProvider.list();
            serviceMessage.set(services.size() + " services loaded.");
            selectedService.set(Math.max(0, Math.min(selectedService.get(), services.size() - 1)));
            for (ServiceSnapshot service : services)
                transition("service:" + service.unit(), service.active().equalsIgnoreCase("failed") ? "failed" : "ok",
                        "Service " + service.unit() + " failed", EventLogBuffer.Severity.CRITICAL);
            updateSnapshot(null, null, null, null, services, Map.of());
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); recordProviderError("Services", "Read interrupted"); }
        catch (IOException | RuntimeException e) { recordProviderError("Services", message(e)); serviceMessage.set("Error: " + message(e)); }
    }

    private void updateSnapshot(CpuSnapshot cpu, MemorySnapshot memory, GpuReading gpu,
            DiskSnapshot disk, List<ServiceSnapshot> services, Map<String, String> errors) {
        DashboardSnapshot old = snapshot.get();
        snapshot.set(new DashboardSnapshot(Instant.now(), cpu != null ? cpu : old == null ? null : old.cpu(),
                memory != null ? memory : old == null ? null : old.memory(), gpu != null ? gpu : old == null ? null : old.gpu(),
                disk != null ? disk : old == null ? null : old.disk(), services != null ? services : old == null ? List.of() : old.services(),
                errors.isEmpty() && old != null ? old.errors() : errors));
        pageError.set(errors.isEmpty() ? null : errors.values().iterator().next());
    }

    private TimeSeriesBuffer filesystemBuffer(String mount) {
        synchronized (filesystemHistory) { return filesystemHistory.computeIfAbsent(mount, ignored -> new TimeSeriesBuffer()); }
    }

    public EventLogBuffer eventLog() { return eventLog; }

    @Override
    public void close() { scheduler.close(); }

    private void filesystemEvent(DiskSnapshot.Filesystem filesystem) {
        Double value = filesystem.utilizationPercent();
        String state = value == null ? "unknown" : value >= 95 ? "critical" : value >= 85 ? "warning" : "ok";
        transition("filesystem:" + filesystem.mountPoint(), state,
                "Filesystem " + filesystem.mountPoint() + " above threshold (" + state + ")",
                state.equals("critical") ? EventLogBuffer.Severity.CRITICAL : EventLogBuffer.Severity.WARM);
    }

    private void thresholdEvent(String key, Double value, double warning, double critical, String label) {
        String state = value == null ? "unknown" : value >= critical ? "critical" : value >= warning ? "warning" : "ok";
        transition(key, state, label + " above threshold (" + state + ")",
                state.equals("critical") ? EventLogBuffer.Severity.CRITICAL : EventLogBuffer.Severity.WARM);
    }

    private void recordProviderError(String provider, String detail) {
        transition("provider:" + provider, "error", provider + " provider error: " + detail,
                EventLogBuffer.Severity.CRITICAL);
    }

    private void transition(String key, String state, String message, EventLogBuffer.Severity severity) {
        transitions.transition(key, state, severity, message);
    }

    private int serviceCount() {
        DashboardSnapshot current = snapshot.get();
        return current == null ? 0 : current.services().size();
    }

    @Override
    public void render(TextGraphics graphics, TerminalSize size) {
        int width = size.getColumns();
        int height = size.getRows();
        graphics.setBackgroundColor(BACKGROUND);
        graphics.setForegroundColor(TEXT);
        graphics.fill(' ');
        if (width < 48 || height < 15) {
            put(graphics, 1, 1, width - 2, "Thyme requires a terminal of at least 48x15.", ORANGE);
            return;
        }

        drawHeader(graphics, width);
        drawFooter(graphics, width, height);

        int contentTop = 2;
        int contentHeight = height - contentTop - 1;
        int topHeight = contentHeight / 3;
        int storageHeight = Math.min(
                Math.max(4, contentHeight * 2 / 5),
                Math.max(4, contentHeight - topHeight - 3));
        int bottomHeight = contentHeight - topHeight - storageHeight;
        int leftWidth = (width - 1) / 2;
        int rightX = leftWidth + 1;
        int rightWidth = width - rightX;
        DashboardSnapshot current = snapshot.get();
        int storageY = contentTop + topHeight;
        int bottomY = storageY + storageHeight;
        drawPanel(graphics, 0, contentTop, leftWidth, topHeight, "CPU", TEAL);
        drawPanel(graphics, rightX, contentTop, rightWidth, topHeight, "MEMORY", BLUE);
        drawPanel(graphics, 0, storageY, width, storageHeight, "STORAGE / FILESYSTEMS", ORANGE);
        drawPanel(graphics, 0, bottomY, leftWidth, bottomHeight, "GRAPHICS", PURPLE);
        drawPanel(graphics, rightX, bottomY, rightWidth, bottomHeight, "SERVICES", GREEN);

        if (pageError.get() != null)
            put(graphics, 2, 3, width - 4, "ERROR  " + pageError.get(), RED);
        else if (current == null)
            put(graphics, 2, 3, width - 4, "Connecting to system metrics...", MUTED);
        else {
            drawCpu(graphics, current, 0, contentTop, leftWidth, topHeight);
            drawMemory(graphics, current, rightX, contentTop, rightWidth, topHeight);
            drawStorage(graphics, current, 0, storageY, width, storageHeight);
            drawGpu(graphics, current, 0, bottomY, leftWidth, bottomHeight);
            drawServices(graphics, current, rightX, bottomY, rightWidth, bottomHeight);
        }
    }

    private void drawHeader(TextGraphics graphics, int width) {
        graphics.setBackgroundColor(BACKGROUND);
        put(graphics, 1, 0, width - 2, " THYME  /  SYSTEM MONITOR ", TEAL, true);
        put(graphics, Math.max(30, width - 22), 0, 20,
                snapshot.get() == null ? "LIVE  •  --:--:--" : "LIVE  •  " + CLOCK.format(snapshot.get().sampledAt()),
                MUTED);
        graphics.setForegroundColor(BORDER);
        if (width > 0)
            graphics.drawLine(0, 1, width - 1, 1, '─');
    }

    private void drawFooter(TextGraphics graphics, int width, int height) {
        graphics.setBackgroundColor(BACKGROUND);
        graphics.setForegroundColor(BORDER);
        graphics.drawLine(0, height - 1, width - 1, height - 1, '─');
        put(graphics, 1, height - 1, width - 2,
                " d containers   •   ↑/↓ select service   •   r refresh   •   q quit ", MUTED);
    }

    private static void drawPanel(
            TextGraphics graphics, int x, int y, int width, int height, String title, TextColor accent) {
        if (width < 4 || height < 3)
            return;
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

    private void drawCpu(TextGraphics graphics, DashboardSnapshot current, int x, int y, int width, int height) {
        CpuSnapshot cpu = current.cpu();
        int row = y + 1;
        if (cpu == null) {
            put(graphics, x + 2, row, width - 4, "Unavailable: " + current.errors().getOrDefault("CPU", "no data"), RED);
            return;
        }
        int bottom = y + height - 1;
        if (row >= bottom)
            return;
        put(graphics, x + 2, row, width - 4, shorten(cpu.model(), width - 5), TEXT);
        row++;
        if (row < bottom)
            row = gauge(graphics, x + 2, row, width - 4, "TOTAL", cpu.totalUsagePercent(), TEAL);
        if (row < bottom) {
            put(graphics, x + 2, row, width - 4,
                    "15m " + sparkline(cpuHistory.samples(TimeSeriesBuffer.Range.FIFTEEN_MINUTES), width - 18)
                            + age(cpuHistory), TEAL);
            row++;
        }
        if (row < bottom) {
            put(graphics, x + 2, row, width - 4,
                "Temp " + temperature(cpu.temperatureCelsius()) + "   Load "
                        + decimal(cpu.loadAverage1()) + "  " + decimal(cpu.loadAverage5()) + "  "
                        + decimal(cpu.loadAverage15()) + "   " + cpu.logicalProcessors() + " threads",
                MUTED);
            row++;
        }
        int coresPerLine = Math.max(1, (width - 5) / 12);
        for (int index = 0; index < cpu.cores().size() && row < bottom; index += coresPerLine) {
            StringBuilder line = new StringBuilder();
            for (int core = index; core < Math.min(index + coresPerLine, cpu.cores().size()); core++) {
                if (!line.isEmpty())
                    line.append("  ");
                CpuSnapshot.Core value = cpu.cores().get(core);
                line.append("C").append(value.index()).append(' ')
                        .append(percent(value.usagePercent())).append(' ')
                        .append(shortFrequency(value.frequencyHz()));
            }
            put(graphics, x + 2, row, width - 4, line.toString(), BLUE);
            row++;
        }
    }

    private void drawMemory(TextGraphics graphics, DashboardSnapshot current, int x, int y, int width, int height) {
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

    private void drawGpu(TextGraphics graphics, DashboardSnapshot current, int x, int y, int width, int height) {
        GpuReading gpu = current.gpu();
        if (gpu == null || gpu.devices().isEmpty()) {
            put(graphics, x + 2, y + 1, width - 4, "No graphics device detected", MUTED);
            if (gpu != null && !gpu.errors().isEmpty())
                put(graphics, x + 2, y + 2, width - 4, shorten(String.join("; ", gpu.errors()), width - 5), ORANGE);
            return;
        }
        int row = y + 1;
        for (GpuSnapshot device : gpu.devices()) {
            if (row >= y + height - 1)
                break;
            put(graphics, x + 2, row, width - 4,
                    shorten(device.vendor() + "  " + device.name(), width - 5), TEXT);
            row++;
            if (row < y + height - 1)
                row = gauge(graphics, x + 2, row, width - 4, "GPU", device.utilizationPercent(), PURPLE);
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
        if (error != null && row < y + height - 1)
            put(graphics, x + 2, row, width - 4, "Partial metrics: " + error, ORANGE);
    }

    private void drawStorage(TextGraphics graphics, DashboardSnapshot current, int x, int y, int width, int height) {
        DiskSnapshot disk = current.disk();
        if (disk == null) {
            put(graphics, x + 2, y + 1, width - 4,
                    "Unavailable: " + current.errors().getOrDefault("Disks", "no data"), RED);
            return;
        }
        if (disk.filesystems().isEmpty()) {
            put(graphics, x + 2, y + 1, width - 4, "No user-visible mounted filesystems detected", MUTED);
            return;
        }
        int row = y + 1;
        int maxRows = y + height - 1;
        for (DiskSnapshot.Filesystem filesystem : disk.filesystems()) {
            if (row + 1 >= maxRows)
                break;
            double usedPercent = ratio(filesystem.usedBytes(), filesystem.totalBytes());
            String mount = shorten(filesystem.mountPoint(), Math.max(12, width / 4));
            String space = gib(filesystem.usedBytes()) + " / " + gib(filesystem.totalBytes()) + " GiB ("
                    + percent(usedPercent) + ")";
            int mountWidth = Math.max(12, width / 5);
            put(graphics, x + 2, row, mountWidth, mount, rowColor(usedPercent), true);
            put(graphics, x + 4 + mountWidth, row, width - mountWidth - 8, space, rowColor(usedPercent));
            String io = "R " + rate(filesystem.readBytesPerSecond())
                    + " W " + rate(filesystem.writeBytesPerSecond())
                    + " I/O " + percent(filesystem.utilizationPercent())
                    + " Lat " + millis(filesystem.averageReadLatencyMillis())
                    + "/" + millis(filesystem.averageWriteLatencyMillis()) + "ms";
            int graphWidth = width / 3;
            int ioWidth = width - graphWidth - 8;
            put(graphics, x + 2, row + 1, ioWidth, io, rowColor(filesystem.utilizationPercent()));
            double[] history;
            synchronized (filesystemHistory) {
                TimeSeriesBuffer buffer = filesystemHistory.get(filesystem.mountPoint());
                history = buffer == null ? new double[0]
                        : buffer.samples(TimeSeriesBuffer.Range.FIFTEEN_MINUTES);
            }
            put(graphics, x + 4 + ioWidth, row + 1, graphWidth,
                    sparkline(history, graphWidth), ORANGE);
            row += 2;
        }
    }

    private void drawServices(
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
        if (height > 3)
            put(graphics, x + 2, y + height - 2, width - 4, serviceMessage.get(), MUTED);
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

    private static TextColor rowColor(Double utilization) {
        if (utilization == null)
            return MUTED;
        return utilization >= 85 ? RED : utilization >= 65 ? ORANGE : TEXT;
    }

    private static TextColor serviceColor(ServiceSnapshot service) {
        return service.active().equalsIgnoreCase("active") ? GREEN : MUTED;
    }

    private static void put(TextGraphics graphics, int x, int y, int width, String text, TextColor color) {
        put(graphics, x, y, width, text, color, false);
    }

    private static void put(
            TextGraphics graphics, int x, int y, int width, String text, TextColor color, boolean bold) {
        if (width <= 0 || text == null)
            return;
        String clipped = text.substring(0, Math.min(text.length(), width));
        graphics.setForegroundColor(color);
        if (bold)
            graphics.putString(x, y, clipped, com.googlecode.lanterna.SGR.BOLD);
        else
            graphics.putString(x, y, clipped);
    }

    private static String message(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    private static String shorten(String value, int maxLength) {
        if (value.length() <= maxLength)
            return value;
        return maxLength <= 1 ? value.substring(0, maxLength) : value.substring(0, maxLength - 1) + "…";
    }

    private static Double ratio(long used, long total) {
        return total == 0 ? 0.0 : Math.min(100, used * 100.0 / total);
    }

    private static String percent(Double value) {
        return value == null ? "n/a" : String.format("%.0f%%", value);
    }

    private static String decimal(Double value) {
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

    private static String millis(Double value) {
        return value == null ? "--" : String.format("%.1f", value);
    }

    private static String age(TimeSeriesBuffer buffer) {
        Duration age = buffer.latestAge();
        if (age == null || age.compareTo(Duration.ofSeconds(2)) <= 0) return "";
        return "  age " + Math.max(1, age.toSeconds()) + "s";
    }

    private static String rate(Double bytesPerSecond) {
        if (bytesPerSecond == null)
            return "--";
        double mib = bytesPerSecond / (1024.0 * 1024);
        return mib >= 100 ? String.format("%.0fM/s", mib) : String.format("%.1fM/s", mib);
    }

    private static String sparkline(double[] values, int width) {
        if (values.length == 0 || width <= 0)
            return "waiting for samples";
        int start = Math.max(0, values.length - width);
        StringBuilder line = new StringBuilder(values.length - start);
        for (int index = start; index < values.length; index++) {
            if (Double.isNaN(values[index])) { line.append(' '); continue; }
            double value = Math.max(0, Math.min(100, values[index]));
            line.append(SPARKLINE[(int) Math.round(value / 100 * (SPARKLINE.length - 1))]);
        }
        return line.toString();
    }
}
