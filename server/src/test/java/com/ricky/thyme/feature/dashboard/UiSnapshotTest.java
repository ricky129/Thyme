package com.ricky.thyme.feature.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.virtual.DefaultVirtualTerminal;
import com.ricky.thyme.feature.cpu.CpuSnapshot;
import com.ricky.thyme.feature.disk.DiskSnapshot;
import com.ricky.thyme.feature.host.HostSnapshot;
import com.ricky.thyme.feature.memory.MemorySnapshot;
import com.ricky.thyme.feature.telemetry.EventLogBuffer;
import com.ricky.thyme.feature.telemetry.TimeSeriesBuffer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Text snapshots for the responsive overview and KPI detail overlay. */
class UiSnapshotTest {
    private static final List<TerminalSize> SIZES = List.of(
            new TerminalSize(48, 15), new TerminalSize(80, 24),
            new TerminalSize(120, 35), new TerminalSize(200, 60));

    @Test
    void writesOverviewAndDetailSnapshotsAtAllSupportedSizes() throws IOException {
        DashboardSnapshot snapshot = snapshot();
        TimeSeriesBuffer cpu = history(42, 900);
        TimeSeriesBuffer memory = history(68, 900);
        TimeSeriesBuffer temperature = history(54, 900);
        TimeSeriesBuffer gpu = history(12, 900);
        TimeSeriesBuffer disk = history(3.5, 900);
        EventLogBuffer events = new EventLogBuffer();
        events.add(new EventLogBuffer.Event(Instant.parse("2026-01-01T12:00:00Z"),
                EventLogBuffer.Severity.WARM, "CPU threshold"));

        for (TerminalSize size : SIZES) {
            writeSnapshot("overview", size, (graphics, terminalSize) ->
                    DashboardOverviewRenderer.draw(graphics, terminalSize, snapshot, cpu, memory,
                            temperature, gpu, disk, events, null, 0, false));
            writeSnapshot("detail", size, (graphics, terminalSize) ->
                    DashboardOverviewRenderer.draw(graphics, terminalSize, snapshot, cpu, memory,
                            temperature, gpu, disk, events, null, 0, true,
                            TimeSeriesBuffer.Range.FIFTEEN_MINUTES, 899, false, true));
        }
        Path overview = Path.of("target", "ui-snapshots", "overview-120x35.txt");
        assertTrue(Files.exists(overview));
        String overviewText = Files.readString(overview);
        assertTrue(overviewText.contains("myserver"), "header must show the hostname");
        assertTrue(overviewText.contains("up 12d 4h"), "header must show the uptime");
        assertTrue(overviewText.contains("CPU") && overviewText.contains("RAM")
                && overviewText.contains("Temp") && overviewText.contains("GPU")
                && overviewText.contains("Disk I/O"),
                "home collage must show all five metric panels");
        assertTrue(overviewText.contains("Failed events"),
                "wide home view must keep the failed events sidebar");
        assertTrue(overviewText.contains("-15m") && overviewText.contains("now"),
                "charts must label the x axis with time");
        assertFalse(overviewText.contains("Core 0"),
                "home collage must show total CPU only, not per-core charts");
        String detailText = Files.readString(Path.of("target", "ui-snapshots", "detail-120x35.txt"));
        assertTrue(detailText.contains("-15m") && detailText.contains("now"));
        assertTrue(detailText.contains("● total"));
    }

    @Test
    void writesPerCoreSnapshotsWithOneGraphPerCore() throws IOException {
        DashboardSnapshot snapshot = snapshotWithCores();
        List<TimeSeriesBuffer> cores = List.of(history(10, 900), history(20, 900),
                history(30, 900), history(40, 900));
        int[] cursors = {89, -1, 50, -1};
        for (TerminalSize size : SIZES) {
            writeSnapshot("per-core", size, (graphics, terminalSize) ->
                    DashboardOverviewRenderer.draw(graphics, terminalSize, snapshot,
                            history(25, 18), history(68, 900), history(5, 900), history(54, 900),
                            history(12, 900), history(3.5, 900), new EventLogBuffer(), null,
                            0, true, TimeSeriesBuffer.Range.ONE_HOUR, 17, true, true,
                            cores, 2, cursors));
        }
        Path snapshotFile = Path.of("target", "ui-snapshots", "per-core-120x35.txt");
        assertTrue(Files.exists(snapshotFile));
        String content = Files.readString(snapshotFile);
        assertEquals(1, content.lines()
                .filter(line -> line.contains("history covers 18s of 1h"))
                .count());
        assertTrue(content.contains("Core 0 ▸ cursor"));
        assertTrue(content.contains("Core 2 * ▸ cursor"));
    }

    @Test
    void rendersUsedAndSwapSeriesInRamDetail() throws IOException {
        TerminalSize size = new TerminalSize(120, 35);
        DashboardSnapshot snapshot = snapshot();
        writeSnapshot("ram-detail", size, (graphics, terminalSize) ->
                DashboardOverviewRenderer.draw(graphics, terminalSize, snapshot,
                        history(42, 900), history(68, 150), history(12, 150),
                        history(54, 900), history(12, 900), history(3.5, 900),
                        new EventLogBuffer(), null, 1, true,
                        TimeSeriesBuffer.Range.FIFTEEN_MINUTES, 149, false, true,
                        List.of(), 0, new int[0]));
        String text = Files.readString(Path.of("target", "ui-snapshots", "ram-detail-120x35.txt"));
        assertTrue(text.contains("● used"));
        assertTrue(text.contains("○ swap"));
        assertTrue(text.contains("··"), "swap dots should be adjacent column by column");
    }

    @Test
    void hidesSwapDashesWhenSwapUsageIsZero() throws IOException {
        TerminalSize size = new TerminalSize(120, 35);
        DashboardSnapshot snapshot = snapshot();
        writeSnapshot("ram-detail-zero-swap", size, (graphics, terminalSize) ->
                DashboardOverviewRenderer.draw(graphics, terminalSize, snapshot,
                        history(42, 900), history(68, 150), flatHistory(0, 150),
                        history(54, 900), history(12, 900), history(3.5, 900),
                        new EventLogBuffer(), null, 1, true,
                        TimeSeriesBuffer.Range.FIFTEEN_MINUTES, -1, false, true,
                        List.of(), 0, new int[0]));
        String text = Files.readString(
                Path.of("target", "ui-snapshots", "ram-detail-zero-swap-120x35.txt"));
        assertTrue(text.contains("○ swap"));
        assertFalse(text.contains("·█·"), "zero swap must not draw dashed samples");
    }

    @Test
    void drawsSwapDownToZeroBeforeHidingIt() throws IOException {
        TerminalSize size = new TerminalSize(120, 35);
        DashboardSnapshot snapshot = snapshot();
        writeSnapshot("ram-detail-swap-drop", size, (graphics, terminalSize) ->
                DashboardOverviewRenderer.draw(graphics, terminalSize, snapshot,
                        history(42, 900), history(68, 150), swapDropHistory(150),
                        history(54, 900), history(12, 900), history(3.5, 900),
                        new EventLogBuffer(), null, 1, true,
                        TimeSeriesBuffer.Range.FIFTEEN_MINUTES, -1, false, true,
                        List.of(), 0, new int[0]));
        String text = Files.readString(
                Path.of("target", "ui-snapshots", "ram-detail-swap-drop-120x35.txt"));
        assertTrue(text.contains("··"), "swap line should be drawn while in use");
        assertTrue(text.lines().anyMatch(line -> line.contains("0%") && line.contains("·")),
                "swap line should reach the zero baseline before disappearing");
    }

    @Test
    void writesFilesystemOverviewAndDiskDetailSnapshots() throws IOException {
        DashboardSnapshot snapshot = snapshotWithFilesystems();
        List<FilesystemSeries> filesystems = filesystemSeries(snapshot);
        TimeSeriesBuffer cpu = history(42, 900);
        TimeSeriesBuffer memory = history(68, 900);
        TimeSeriesBuffer temperature = history(54, 900);
        TimeSeriesBuffer gpu = history(12, 900);
        TimeSeriesBuffer disk = history(3.5, 900);
        TimeSeriesBuffer swap = history(5, 900);
        for (TerminalSize size : SIZES) {
            writeSnapshot("filesystem-overview", size, (graphics, terminalSize) ->
                    DashboardOverviewRenderer.draw(graphics, terminalSize, snapshot, cpu, memory,
                            swap, temperature, gpu, disk, new EventLogBuffer(), null, 0, false,
                            TimeSeriesBuffer.Range.FIFTEEN_MINUTES, -1, false, true,
                            List.<TimeSeriesBuffer>of(), 0, new int[0], filesystems, false));
            writeSnapshot("disk-detail", size, (graphics, terminalSize) ->
                    DashboardOverviewRenderer.draw(graphics, terminalSize, snapshot, cpu, memory,
                            swap, temperature, gpu, disk, new EventLogBuffer(), null,
                            OverviewCard.SYSTEM_CARD_COUNT, true,
                            TimeSeriesBuffer.Range.FIFTEEN_MINUTES, 898, false, true,
                            List.<TimeSeriesBuffer>of(), 0, new int[0], filesystems, false,
                            false, "", 1));
        }
        writeSnapshot("disk-detail-write", new TerminalSize(120, 35), (graphics, terminalSize) ->
                DashboardOverviewRenderer.draw(graphics, terminalSize, snapshot, cpu, memory,
                        swap, temperature, gpu, disk, new EventLogBuffer(), null,
                        OverviewCard.SYSTEM_CARD_COUNT, true,
                        TimeSeriesBuffer.Range.FIFTEEN_MINUTES, 898, false, true,
                        List.<TimeSeriesBuffer>of(), 0, new int[0], filesystems, true,
                        false, "", 1));
        String overview = Files.readString(
                Path.of("target", "ui-snapshots", "filesystem-overview-120x35.txt"));
        assertTrue(overview.contains("Disk /"));
        assertFalse(overview.contains("/home"));
        assertFalse(overview.contains("/media/usb"));
        String detail = Files.readString(
                Path.of("target", "ui-snapshots", "disk-detail-120x35.txt"));
        assertTrue(detail.contains("Disk I/O"));
        assertTrue(detail.contains("(2/3)"));
        assertTrue(detail.contains("/home"));
        assertTrue(detail.contains("I/O: [Read] Write (Tab)"));
        assertTrue(detail.contains("Disk: / [/home] /media/usb (↑↓)"));
        assertTrue(detail.contains("8M/s"),
                "the axis must scale to the visible samples instead of a fixed range");
        String writeDetail = Files.readString(
                Path.of("target", "ui-snapshots", "disk-detail-write-120x35.txt"));
        assertTrue(writeDetail.contains("I/O: Read [Write] (Tab)"));
        assertTrue(writeDetail.contains("/home · write"));
    }

    @Test
    void keepsSingleDiskTotalCardWhenNoFilesystemsExist() throws IOException {
        TerminalSize size = new TerminalSize(120, 35);
        writeSnapshot("disk-total-fallback", size, (graphics, terminalSize) ->
                DashboardOverviewRenderer.draw(graphics, terminalSize, snapshot(),
                        history(42, 900), history(68, 900), history(54, 900), history(12, 900),
                        history(3.5, 900), new EventLogBuffer(), null, 0, false));
        String text = Files.readString(
                Path.of("target", "ui-snapshots", "disk-total-fallback-120x35.txt"));
        assertTrue(text.contains("Disk I/O"));
        assertEquals(1, occurrences(text, "Disk I/O"));
    }

    private static DashboardSnapshot snapshot() {
        CpuSnapshot cpu = new CpuSnapshot("fixture cpu", "x86_64", 1, 42.0, 54.0,
                List.of(new CpuSnapshot.Core(0, 42.0, 2_000_000_000L)));
        return new DashboardSnapshot(Instant.parse("2026-01-01T12:00:00Z"), cpu,
                new MemorySnapshot(68L * 1024 * 1024 * 1024, 16L * 1024 * 1024 * 1024,
                        1L * 1024 * 1024 * 1024, 0), null, null, List.of(), Map.of(),
                host());
    }

    private static HostSnapshot host() {
        return new HostSnapshot("myserver", 12L * 86400 + 4 * 3600);
    }

    private static DashboardSnapshot snapshotWithFilesystems() {
        List<DiskSnapshot.Filesystem> filesystems = List.of(
                filesystem("/", "ext4", "/dev/nvme0n1p2", 120, 480, 1.2, 0.8, 12.0),
                filesystem("/home", "ext4", "/dev/nvme0n1p3", 210, 720, 2.4, 0.4, 18.0),
                filesystem("/media/usb", "vfat", "/dev/sda1", 8, 32, 0.1, 0.2, 1.0));
        DiskSnapshot disk = new DiskSnapshot(
                List.of(new DiskSnapshot.Device("nvme0n1", 1_000L * 1024 * 1024 * 1024,
                        3.7 * 1024 * 1024, 1.4 * 1024 * 1024, 10.0, 20.0, 24.0, 0.2, 0.4)),
                filesystems);
        CpuSnapshot cpu = new CpuSnapshot("fixture cpu", "x86_64", 1, 42.0, 54.0,
                List.of(new CpuSnapshot.Core(0, 42.0, 2_000_000_000L)));
        return new DashboardSnapshot(Instant.parse("2026-01-01T12:00:00Z"), cpu,
                new MemorySnapshot(68L * 1024 * 1024 * 1024, 16L * 1024 * 1024 * 1024,
                        1L * 1024 * 1024 * 1024, 0), null, disk, List.of(), Map.of(), host());
    }

    private static DiskSnapshot.Filesystem filesystem(String mountPoint, String type, String source,
            long usedGib, long totalGib, double readMib, double writeMib, double utilization) {
        long total = totalGib * 1024 * 1024 * 1024;
        long used = usedGib * 1024 * 1024 * 1024;
        return new DiskSnapshot.Filesystem(source, mountPoint, type, total, total - used, used,
                List.of(source.replace("/dev/", "")), readMib * 1024 * 1024,
                writeMib * 1024 * 1024, utilization, 0.2, 0.3);
    }

    private static List<FilesystemSeries> filesystemSeries(DashboardSnapshot snapshot) {
        List<FilesystemSeries> series = new java.util.ArrayList<>();
        for (DiskSnapshot.Filesystem filesystem : snapshot.disk().filesystems()) {
            series.add(FilesystemSeries.from(filesystem,
                    history(filesystem.readBytesPerSecond() / (1024 * 1024), 900),
                    history(filesystem.writeBytesPerSecond() / (1024 * 1024), 900)));
        }
        return List.copyOf(series);
    }

    private static int occurrences(String text, String value) {
        int count = 0;
        int offset = 0;
        int next = text.indexOf(value, offset);
        while (next >= 0) {
            count++;
            offset = next + value.length();
            next = text.indexOf(value, offset);
        }
        return count;
    }

    private static DashboardSnapshot snapshotWithCores() {
        CpuSnapshot cpu = new CpuSnapshot("fixture cpu", "x86_64", 4, 25.0, 54.0,
                List.of(new CpuSnapshot.Core(0, 10.0, 2_000_000_000L),
                        new CpuSnapshot.Core(1, 20.0, 2_000_000_000L),
                        new CpuSnapshot.Core(2, 30.0, 2_000_000_000L),
                        new CpuSnapshot.Core(3, 40.0, 2_000_000_000L)));
        return new DashboardSnapshot(Instant.parse("2026-01-01T12:00:00Z"), cpu,
                new MemorySnapshot(68L * 1024 * 1024 * 1024, 16L * 1024 * 1024 * 1024,
                        1L * 1024 * 1024 * 1024, 0), null, null, List.of(), Map.of(), host());
    }

    private static TimeSeriesBuffer history(double value, int count) {
        TimeSeriesBuffer history = new TimeSeriesBuffer();
        for (int i = 0; i < count; i++) {
            history.append(i % 37 == 0 ? Double.NaN : value + (i % 5));
        }
        return history;
    }

    private static TimeSeriesBuffer flatHistory(double value, int count) {
        TimeSeriesBuffer history = new TimeSeriesBuffer();
        for (int i = 0; i < count; i++) {
            history.append(value);
        }
        return history;
    }

    private static TimeSeriesBuffer swapDropHistory(int count) {
        TimeSeriesBuffer history = new TimeSeriesBuffer();
        for (int i = 0; i < count; i++) {
            history.append(i < count - 30 ? 40.0 : 0.0);
        }
        return history;
    }

    private static void writeSnapshot(String name, TerminalSize size, Drawer drawer) throws IOException {
        DefaultVirtualTerminal terminal = new DefaultVirtualTerminal(size);
        String text;
        try (Screen screen = new TerminalScreen(terminal)) {
            screen.startScreen();
            drawer.draw(screen.newTextGraphics(), size);
            screen.refresh();
            StringBuilder rendered = new StringBuilder();
            for (int row = 0; row < size.getRows(); row++) {
                for (int column = 0; column < size.getColumns(); column++) {
                    rendered.append(terminal.getCharacter(column, row).getCharacter());
                }
                rendered.append(System.lineSeparator());
            }
            text = rendered.toString();
        }
        Path directory = Path.of("target", "ui-snapshots");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(name + "-" + size.getColumns() + "x" + size.getRows() + ".txt"), text);
    }

    @FunctionalInterface
    private interface Drawer {
        void draw(com.googlecode.lanterna.graphics.TextGraphics graphics, TerminalSize size);
    }
}
