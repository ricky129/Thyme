package com.ricky.thyme.feature.dashboard;

import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.ricky.thyme.feature.cpu.CpuSnapshot;
import com.ricky.thyme.feature.disk.DiskSnapshot;
import com.ricky.thyme.feature.gpu.GpuReading;
import com.ricky.thyme.feature.gpu.GpuSnapshot;
import com.ricky.thyme.feature.telemetry.EventLogBuffer;
import com.ricky.thyme.feature.telemetry.TimeSeriesBuffer;
import com.ricky.thyme.ui.Theme;
import com.ricky.thyme.ui.widgets.Dock;
import com.ricky.thyme.ui.widgets.EventLog;
import com.ricky.thyme.ui.widgets.LineChart;
import com.ricky.thyme.ui.widgets.Panel;
import com.ricky.thyme.ui.widgets.Sparkline;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Render-only responsive composition for the system overview.
 */
public final class DashboardOverviewRenderer {

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    private static final String[] DOCK_LABELS = {"Home", "Containers"};

    private DashboardOverviewRenderer() {
        // Utility class.
    }

    /**
     * Draws the overview with the first KPI selected.
     */
    public static void draw(TextGraphics graphics, TerminalSize size, DashboardSnapshot snapshot,
            TimeSeriesBuffer cpuHistory, TimeSeriesBuffer memoryHistory,
            EventLogBuffer eventLog, String error) {
        draw(graphics, size, snapshot, cpuHistory, memoryHistory,
                new TimeSeriesBuffer(), new TimeSeriesBuffer(), new TimeSeriesBuffer(),
                eventLog, error, 0, false);
    }

    /**
     * Draws the overview at one of the supported terminal breakpoints.
     */
    public static void draw(TextGraphics graphics, TerminalSize size, DashboardSnapshot snapshot,
            TimeSeriesBuffer cpuHistory, TimeSeriesBuffer memoryHistory,
            EventLogBuffer eventLog, String error, int selectedCard) {
        draw(graphics, size, snapshot, cpuHistory, memoryHistory,
                new TimeSeriesBuffer(), new TimeSeriesBuffer(), new TimeSeriesBuffer(),
                eventLog, error, selectedCard, false);
    }

    /**
     * Compatibility overload for callers that only provide CPU and RAM histories.
     */
    public static void draw(TextGraphics graphics, TerminalSize size, DashboardSnapshot snapshot,
            TimeSeriesBuffer cpuHistory, TimeSeriesBuffer memoryHistory,
            EventLogBuffer eventLog, String error, int selectedCard, boolean details) {
        cpuHistory = safeHistory(cpuHistory);
        memoryHistory = safeHistory(memoryHistory);
        eventLog = eventLog == null ? new EventLogBuffer() : eventLog;
        draw(graphics, size, snapshot, cpuHistory, memoryHistory,
                new TimeSeriesBuffer(), new TimeSeriesBuffer(), new TimeSeriesBuffer(),
                eventLog, error, selectedCard, details);
    }

    /**
     * Draws the overview or the selected KPI detail overlay.
     */
    public static void draw(TextGraphics graphics, TerminalSize size, DashboardSnapshot snapshot,
            TimeSeriesBuffer cpuHistory, TimeSeriesBuffer memoryHistory,
            TimeSeriesBuffer temperatureHistory, TimeSeriesBuffer gpuHistory,
            TimeSeriesBuffer diskIoHistory,
            EventLogBuffer eventLog, String error, int selectedCard, boolean details) {
        draw(graphics, size, snapshot, cpuHistory, memoryHistory, new TimeSeriesBuffer(),
                temperatureHistory, gpuHistory, diskIoHistory, eventLog, error, selectedCard, details);
    }

    /** Bounds of one collage tile in terminal coordinates. */
    record TileRect(int cardIndex, int x, int y, int width, int height) {
        boolean contains(int col, int row) {
            return col >= x && col < x + width && row >= y && row < y + height;
        }
    }

    /** Bounds of the centered/full-width drill-in overlay. */
    record OverlayRect(int x, int y, int width, int height) {
        boolean contains(int col, int row) {
            return col >= x && col < x + width && row >= y && row < y + height;
        }
    }

    static List<TileRect> computeTiles(TerminalSize size) {
        if (size == null || size.getColumns() < 48 || size.getRows() < 15) {
            return List.of();
        }
        int width = size.getColumns();
        int height = size.getRows();
        int contentY = 2;
        int footerY = height - 1;
        int contentHeight = footerY - contentY;
        boolean showEvents = width >= 120;
        int eventsWidth = showEvents ? Math.max(28, Math.min(48, width / 3)) : 0;
        int collageWidth = showEvents ? width - eventsWidth - 1 : width;
        int colGap = 1;
        int leftWidth = (collageWidth - colGap) / 2;
        int rightX = leftWidth + colGap;
        int rightWidth = collageWidth - rightX;
        int rowGap = contentHeight >= 26 ? 1 : 0;
        int usableHeight = contentHeight - rowGap * 2;
        int row0Height = usableHeight / 3;
        int row1Height = (usableHeight - row0Height) / 2;
        int row2Height = usableHeight - row0Height - row1Height;
        int row0Y = contentY;
        int row1Y = row0Y + row0Height + rowGap;
        int row2Y = row1Y + row1Height + rowGap;
        return List.of(
                new TileRect(0, 0, row0Y, leftWidth, row0Height),
                new TileRect(1, rightX, row0Y, rightWidth, row0Height),
                new TileRect(2, 0, row1Y, leftWidth, row1Height),
                new TileRect(3, rightX, row1Y, rightWidth, row1Height),
                new TileRect(4, 0, row2Y, collageWidth, row2Height));
    }

    static int hitTestTile(TerminalSize size, int col, int row) {
        for (TileRect tile : computeTiles(size)) {
            if (tile.contains(col, row)) {
                return tile.cardIndex();
            }
        }
        return -1;
    }

    static OverlayRect computeOverlayRect(TerminalSize size) {
        if (size == null || size.getColumns() < 48 || size.getRows() < 15) {
            return null;
        }
        return computeOverlayRect(size.getColumns(), size.getRows() - 1);
    }

    private static OverlayRect computeOverlayRect(int width, int height) {
        boolean fullWidth = width < 100;
        int overlayWidth = fullWidth ? width
                : Math.max(44, Math.min(width - 4, (int) Math.round(width * 0.85)));
        int overlayHeight = Math.max(11, Math.min(height - 1, (int) Math.round(height * 0.90)));
        int x = fullWidth ? 0 : (width - overlayWidth) / 2;
        int y = Math.max(2, (height - overlayHeight) / 2 + 1);
        return new OverlayRect(x, y, overlayWidth, overlayHeight);
    }

    /**
     * Draws the overview/detail with an explicit swap history.
     */
    public static void draw(TextGraphics graphics, TerminalSize size, DashboardSnapshot snapshot,
            TimeSeriesBuffer cpuHistory, TimeSeriesBuffer memoryHistory, TimeSeriesBuffer swapHistory,
            TimeSeriesBuffer temperatureHistory, TimeSeriesBuffer gpuHistory,
            TimeSeriesBuffer diskIoHistory,
            EventLogBuffer eventLog, String error, int selectedCard, boolean details) {
        int width = size.getColumns();
        int height = size.getRows();
        graphics.setBackgroundColor(Theme.PAGE);
        graphics.setForegroundColor(Theme.INK);
        graphics.fill(' ');
        if (width < 48 || height < 15) {
            Theme.put(graphics, 1, 1, Math.max(0, width - 2),
                    "Thyme requires a terminal of at least 48x15.", Theme.WARM);
            return;
        }

        drawHeader(graphics, width, snapshot);
        int footerY = height - 1;
        drawFooter(graphics, width, footerY, false, "");
        drawOverview(graphics, size, snapshot, cpuHistory, memoryHistory,
                temperatureHistory, gpuHistory, diskIoHistory, eventLog, error,
                selectedCard, List.of(), "");
        if (details) {
            drawDetail(graphics, width, footerY, snapshot, cpuHistory, memoryHistory,
                    swapHistory, temperatureHistory, gpuHistory, diskIoHistory, selectedCard,
                    TimeSeriesBuffer.Range.FIFTEEN_MINUTES, -1, false, true, List.of(), 0,
                    new int[0], List.of(), false, -1);
        }
    }

    private static void drawOverview(TextGraphics graphics, TerminalSize size, DashboardSnapshot snapshot,
            TimeSeriesBuffer cpuHistory, TimeSeriesBuffer memoryHistory,
            TimeSeriesBuffer temperatureHistory, TimeSeriesBuffer gpuHistory, TimeSeriesBuffer diskIoHistory,
            EventLogBuffer eventLog, String error, int selectedCard,
            List<FilesystemSeries> filesystems, String eventFilter) {
        cpuHistory = safeHistory(cpuHistory);
        memoryHistory = safeHistory(memoryHistory);
        temperatureHistory = safeHistory(temperatureHistory);
        gpuHistory = safeHistory(gpuHistory);
        diskIoHistory = safeHistory(diskIoHistory);
        filesystems = filesystems == null ? List.of() : filesystems;
        eventLog = eventLog == null ? new EventLogBuffer() : eventLog;
        int width = size.getColumns();
        int height = size.getRows();
        int contentHeight = height - 3;
        List<OverviewCard> cards = OverviewCard.cards(snapshot);
        int clampedSelected = Math.max(0, Math.min(selectedCard, cards.size() - 1));
        for (TileRect tile : computeTiles(size)) {
            OverviewCard card = cards.get(tile.cardIndex());
            drawCollageTile(graphics, tile, card, snapshot, cpuHistory, memoryHistory,
                    temperatureHistory, gpuHistory, diskIoHistory, filesystems, error,
                    tile.cardIndex() == clampedSelected);
        }
        if (width >= 120) {
            int eventsWidth = Math.max(28, Math.min(48, width / 3));
            drawEvents(graphics, width - eventsWidth, 2, eventsWidth, contentHeight, eventLog, eventFilter);
        }
    }

    /**
     * Draws the overview and a detail overlay using the current interaction state.
     */
    public static void draw(TextGraphics graphics, TerminalSize size, DashboardSnapshot snapshot,
            TimeSeriesBuffer cpuHistory, TimeSeriesBuffer memoryHistory,
            TimeSeriesBuffer temperatureHistory, TimeSeriesBuffer gpuHistory,
            TimeSeriesBuffer diskIoHistory,
            EventLogBuffer eventLog, String error, int selectedCard, boolean details,
            TimeSeriesBuffer.Range range, int cursor, boolean perCore, boolean explainer) {
        draw(graphics, size, snapshot, cpuHistory, memoryHistory, new TimeSeriesBuffer(), temperatureHistory, gpuHistory,
                diskIoHistory, eventLog, error, selectedCard, details, range, cursor, perCore,
                explainer, List.of(), 0, new int[0]);
    }

    /**
     * Draws the overview and detail overlay with per-core CPU histories.
     */
    public static void draw(TextGraphics graphics, TerminalSize size, DashboardSnapshot snapshot,
            TimeSeriesBuffer cpuHistory, TimeSeriesBuffer memoryHistory, TimeSeriesBuffer swapHistory,
            TimeSeriesBuffer temperatureHistory, TimeSeriesBuffer gpuHistory,
            TimeSeriesBuffer diskIoHistory, EventLogBuffer eventLog, String error,
            int selectedCard, boolean details, TimeSeriesBuffer.Range range, int cursor,
            boolean perCore, boolean explainer, List<TimeSeriesBuffer> coreHistories,
            int selectedCore, int[] coreCursors) {
        draw(graphics, size, snapshot, cpuHistory, memoryHistory, swapHistory,
                temperatureHistory, gpuHistory, diskIoHistory, eventLog, error,
                selectedCard, details, range, cursor, perCore, explainer,
                coreHistories, selectedCore, coreCursors, List.of(), false);
    }

    /**
     * Draws the overview and detail overlay with per-filesystem I/O tiles.
     */
    public static void draw(TextGraphics graphics, TerminalSize size, DashboardSnapshot snapshot,
            TimeSeriesBuffer cpuHistory, TimeSeriesBuffer memoryHistory, TimeSeriesBuffer swapHistory,
            TimeSeriesBuffer temperatureHistory, TimeSeriesBuffer gpuHistory,
            TimeSeriesBuffer diskIoHistory, EventLogBuffer eventLog, String error,
            int selectedCard, boolean details, TimeSeriesBuffer.Range range, int cursor,
            boolean perCore, boolean explainer, List<TimeSeriesBuffer> coreHistories,
            int selectedCore, int[] coreCursors, List<FilesystemSeries> filesystems,
            boolean filesystemWrite) {
        draw(graphics, size, snapshot, cpuHistory, memoryHistory, swapHistory,
                temperatureHistory, gpuHistory, diskIoHistory, eventLog, error,
                selectedCard, details, range, cursor, perCore, explainer,
                coreHistories, selectedCore, coreCursors, filesystems,
                filesystemWrite, false, "");
    }

    /**
     * Draws the overview and detail overlay with support for event log filtering.
     */
    public static void draw(TextGraphics graphics, TerminalSize size, DashboardSnapshot snapshot,
            TimeSeriesBuffer cpuHistory, TimeSeriesBuffer memoryHistory, TimeSeriesBuffer swapHistory,
            TimeSeriesBuffer temperatureHistory, TimeSeriesBuffer gpuHistory,
            TimeSeriesBuffer diskIoHistory, EventLogBuffer eventLog, String error,
            int selectedCard, boolean details, TimeSeriesBuffer.Range range, int cursor,
            boolean perCore, boolean explainer, List<TimeSeriesBuffer> coreHistories,
            int selectedCore, int[] coreCursors, List<FilesystemSeries> filesystems,
            boolean filesystemWrite, boolean filteringEvents, String eventFilter) {
        draw(graphics, size, snapshot, cpuHistory, memoryHistory, swapHistory,
                temperatureHistory, gpuHistory, diskIoHistory, eventLog, error,
                selectedCard, details, range, cursor, perCore, explainer,
                coreHistories, selectedCore, coreCursors, filesystems,
                filesystemWrite, filteringEvents, eventFilter, -1);
    }

    /**
     * Draws the overview and detail overlay with an explicit selected filesystem index.
     */
    public static void draw(TextGraphics graphics, TerminalSize size, DashboardSnapshot snapshot,
            TimeSeriesBuffer cpuHistory, TimeSeriesBuffer memoryHistory, TimeSeriesBuffer swapHistory,
            TimeSeriesBuffer temperatureHistory, TimeSeriesBuffer gpuHistory,
            TimeSeriesBuffer diskIoHistory, EventLogBuffer eventLog, String error,
            int selectedCard, boolean details, TimeSeriesBuffer.Range range, int cursor,
            boolean perCore, boolean explainer, List<TimeSeriesBuffer> coreHistories,
            int selectedCore, int[] coreCursors, List<FilesystemSeries> filesystems,
            boolean filesystemWrite, boolean filteringEvents, String eventFilter,
            int selectedFilesystem) {
        filesystems = filesystems == null ? List.of() : filesystems;
        eventLog = eventLog == null ? new EventLogBuffer() : eventLog;
        int width = size.getColumns();
        int height = size.getRows();
        graphics.setBackgroundColor(Theme.PAGE);
        graphics.setForegroundColor(Theme.INK);
        graphics.fill(' ');
        if (width < 48 || height < 15) {
            Theme.put(graphics, 1, 1, Math.max(0, width - 2),
                    "Thyme requires a terminal of at least 48x15.", Theme.WARM);
            return;
        }
        drawHeader(graphics, width, snapshot);
        drawFooter(graphics, width, height - 1, filteringEvents, eventFilter);
        drawOverview(graphics, size, snapshot, cpuHistory, memoryHistory,
                temperatureHistory, gpuHistory, diskIoHistory, eventLog, error,
                selectedCard, filesystems, eventFilter);
        if (details) {
            drawDetail(graphics, width, height - 1, snapshot, cpuHistory, memoryHistory,
                    swapHistory, temperatureHistory, gpuHistory, diskIoHistory, selectedCard,
                    range, cursor, perCore, explainer, coreHistories, selectedCore, coreCursors,
                    filesystems, filesystemWrite, selectedFilesystem);
        }
    }

    private static TimeSeriesBuffer safeHistory(TimeSeriesBuffer history) {
        return history == null ? new TimeSeriesBuffer() : history;
    }

    private static void drawDetail(TextGraphics graphics, int width, int height,
            DashboardSnapshot snapshot, TimeSeriesBuffer cpuHistory,
            TimeSeriesBuffer memoryHistory, TimeSeriesBuffer swapHistory,
            TimeSeriesBuffer temperatureHistory, TimeSeriesBuffer gpuHistory,
            TimeSeriesBuffer diskIoHistory, int selectedCard,
            TimeSeriesBuffer.Range range, int cursor, boolean perCore, boolean explainer,
            List<TimeSeriesBuffer> coreHistories, int selectedCore, int[] coreCursors,
            List<FilesystemSeries> filesystems, boolean filesystemWrite, int selectedFilesystem) {
        OverviewCard card = cardAt(snapshot, selectedCard);
        if (card.filesystem()) {
            drawFilesystemDetail(graphics, width, height, filesystems, card,
                    range, cursor, explainer, filesystemWrite, selectedFilesystem);
            return;
        }
        String label = card.detailLabel();
        OverlayRect rect = computeOverlayRect(width, height);
        boolean fullWidth = width < 100;
        int overlayWidth = rect.width();
        int overlayHeight = rect.height();
        int x = rect.x();
        int y = rect.y();
        graphics.setBackgroundColor(Theme.SURFACE);
        // Full-width overlays clear the whole area below the header to avoid
        // overview residue (panel borders) showing outside the overlay rect.
        int fillHeight = fullWidth ? height - y : overlayHeight;
        graphics.fillRectangle(new TerminalPosition(x, y), new TerminalSize(overlayWidth, fillHeight), ' ');
        Panel.draw(graphics, x, y, overlayWidth, overlayHeight, label + "  ·  Esc close");
        int innerX = x + 2;
        int innerW = overlayWidth - 4;
        TimeSeriesBuffer history = historyForCard(selectedCard, cpuHistory, memoryHistory,
                temperatureHistory, gpuHistory, diskIoHistory);
        Theme.put(graphics, innerX, y + 2, innerW,
                "Current  " + current(history, range, selectedCard) + "   Average  "
                + aggregate(history, selectedCard, range, false) + "   Peak  "
                + aggregate(history, selectedCard, range, true), Theme.INK);
        Theme.put(graphics, innerX, y + 3, innerW,
                rangeTabs(range) + (selectedCard == 0
                ? "       CPU: " + (perCore ? "Total  [Per-core]" : "[Total]  Per-core") + "  (Tab)"
                : ""),
                Theme.INK_SECONDARY);
        if (perCore && card.kind() == OverviewCard.Kind.CPU) {
            int chartY = y + 4;
            boolean compact = overlayHeight < 20;
            int gridHeight = Math.max(3, overlayHeight - (explainer ? 13 : 10));
            if (compact)
                gridHeight = Math.max(1, overlayHeight - 5);
            String coverage = cpuHistory.coverageNote(range);
            if (coverage != null) {
                Theme.put(graphics, innerX, y + 3, innerW,
                        rangeTabs(range) + "  " + coverage + "  "
                        + (perCore ? "CPU: Total  [Per-core]  (Tab)" : ""), Theme.INK_SECONDARY);
            }
            drawPerCoreGrid(graphics, innerX, chartY, innerW, gridHeight,
                    coreHistories, range, selectedCore, coreCursors);
            int detailY = chartY + gridHeight + 1;
            if (!compact && explainer && detailY < y + overlayHeight - 2) {
                Panel.draw(graphics, innerX, detailY, innerW, 3, "What is CPU?");
                Theme.put(graphics, innerX + 1, detailY + 1, innerW - 2,
                        explanation(0), Theme.INK_SECONDARY);
                detailY += 4;
            }
            if (!compact) {
                drawDetailSummary(graphics, snapshot, selectedCard, innerX, detailY, innerW, true, selectedCore);
            }
            Theme.put(graphics, innerX, y + overlayHeight - 1, innerW,
                    footerHint(innerW,
                            "↑↓ core   ←/→ drill-in   h/l cursor   [ ] range   Tab total/per-core   Esc close",
                            "↑↓ core  ←/→ drill  [ ]  Tab  Esc"),
                    Theme.INK_MUTED);
            return;
        }
        boolean compact = overlayHeight < 20;
        double[] samples = history == null ? new double[0] : history.samples(range);
        int chartY = y + 4;
        int chartHeight = Math.max(4, overlayHeight - (explainer ? 13 : 10));
        double[] swapSamples = selectedCard == 1
                ? (swapHistory == null ? new double[0] : swapHistory.samples(range))
                : new double[0];
        double max = selectedCard == 1
                ? Math.max(axisMaximum(1, samples, overlayWidth - 2),
                        axisMaximum(1, swapSamples, overlayWidth - 2))
                : axisMaximum(selectedCard, samples, overlayWidth - 2);
        int noteY = chartY + chartHeight;
        if (history == null) {
            Theme.put(graphics, innerX, chartY, innerW, "history unavailable for this metric", Theme.INK_MUTED);
        } else {
            String coverage = history.coverageNote(range);
            if (selectedCard == 1) {
                LineChart.drawMulti(graphics, x + 1, chartY, overlayWidth - 2, chartHeight, label,
                        chartUnit(selectedCard), List.of(
                        new LineChart.Series("used", samples, Theme.MEMORY_SERIES, false),
                        new LineChart.Series("swap", swapSamples, Theme.SWAP_SERIES, true)),
                        max, cursor, compact ? coverage : null, timeLabels(range), Theme.BRAILLE);
            } else {
                LineChart.draw(graphics, x + 1, chartY, overlayWidth - 2, chartHeight, label,
                        chartUnit(selectedCard), samples, max, seriesColor(selectedCard), cursor,
                        compact ? coverage : null, timeLabels(range), Theme.BRAILLE);
            }
            if (!compact) {
                drawLegend(graphics, innerX, noteY, innerW, legendFor(selectedCard), coverage);
                noteY++;
            }
            if (cursor >= 0 && cursor < samples.length && !Double.isNaN(samples[cursor])) {
                String readout = "▸ cursor " + formatMetric(samples[cursor], selectedCard);
                if (selectedCard == 1 && cursor < swapSamples.length
                        && !Double.isNaN(swapSamples[cursor])) {
                    readout = readout + "   swap " + formatMetric(swapSamples[cursor], 1);
                }
                Theme.put(graphics, innerX, noteY, innerW, readout, Theme.ACCENT);
                noteY++;
            }
        }
        int detailY = noteY;
        if (explainer && detailY < y + overlayHeight - 2) {
            Panel.draw(graphics, innerX, detailY, innerW, 3, "What is " + label + "?");
            Theme.put(graphics, innerX + 1, detailY + 1, innerW - 2, explanation(selectedCard), Theme.INK_SECONDARY);
            detailY += 4;
        }
        drawDetailSummary(graphics, snapshot, selectedCard, innerX, detailY, innerW, perCore, selectedCore);
        Theme.put(graphics, innerX, y + overlayHeight - 1, innerW,
                footerHint(innerW,
                        "←/→ drill-in   h/l cursor   [ ] range   e explain   Esc close",
                        "←/→ drill  h/l cur  [ ] range  e  Esc"),
                Theme.INK_MUTED);
    }

    /**
     * Full key hint when it fits, otherwise a compact variant.
     */
    private static String footerHint(int width, String full, String compact) {
        return width >= full.length() ? full : compact;
    }

    private static OverviewCard cardAt(DashboardSnapshot snapshot, int index) {
        List<OverviewCard> cards = OverviewCard.cards(snapshot);
        return cards.get(Math.max(0, Math.min(index, cards.size() - 1)));
    }

    /**
     * Draws the Disk I/O drill-in overlay; when multiple filesystems exist, {@code selectedFilesystem} chooses the active mount and {@code write} toggles Read/Write.
     */
    private static void drawFilesystemDetail(TextGraphics graphics, int width, int height,
            List<FilesystemSeries> filesystems, OverviewCard card,
            TimeSeriesBuffer.Range range, int cursor, boolean explainer,
            boolean write, int selectedFilesystem) {
        OverlayRect rect = computeOverlayRect(width, height);
        boolean fullWidth = width < 100;
        int overlayWidth = rect.width();
        int overlayHeight = rect.height();
        int x = rect.x();
        int y = rect.y();
        graphics.setBackgroundColor(Theme.SURFACE);
        // Full-width overlays clear the whole area below the header to avoid
        // overview residue (panel borders) showing outside the overlay rect.
        int fillHeight = fullWidth ? height - y : overlayHeight;
        graphics.fillRectangle(new TerminalPosition(x, y), new TerminalSize(overlayWidth, fillHeight), ' ');
        int innerX = x + 2;
        int innerW = overlayWidth - 4;
        if (filesystems == null || filesystems.isEmpty()) {
            Panel.draw(graphics, x, y, overlayWidth, overlayHeight,
                    "Disk I/O  ·  " + card.detailLabel() + "  ·  Esc close");
            Theme.put(graphics, innerX, y + 2, innerW,
                    "Filesystem " + card.detailLabel() + " is no longer available", Theme.INK_MUTED);
            Theme.put(graphics, innerX, y + overlayHeight - 1, innerW,
                    "Esc close", Theme.INK_MUTED);
            return;
        }
        int fsIndex = resolveFilesystemIndex(filesystems, card.mountPoint(), selectedFilesystem);
        FilesystemSeries filesystem = filesystems.get(fsIndex);
        boolean multiFs = filesystems.size() > 1;
        String title = multiFs
                ? "Disk I/O  ·  " + filesystem.mountPoint() + " (" + (fsIndex + 1) + "/" + filesystems.size() + ")  ·  Esc close"
                : "Disk I/O  ·  " + filesystem.mountPoint() + "  ·  Esc close";
        Panel.draw(graphics, x, y, overlayWidth, overlayHeight, title);
        Theme.put(graphics, innerX, y + 2, innerW,
                "Read " + mibRate(mib(filesystem.readBytesPerSecond()))
                + "   Write " + mibRate(mib(filesystem.writeBytesPerSecond()))
                + "   Active " + activeText(filesystem.utilizationPercent()), Theme.INK);
        TimeSeriesBuffer history = write ? filesystem.writeHistory() : filesystem.readHistory();
        String coverage = history.coverageNote(range);
        boolean compact = overlayHeight < 20;
        String baseControls = rangeTabs(range)
                + (compact && coverage != null ? "  " + coverage : "")
                + "   I/O: " + (write ? "Read [Write]" : "[Read] Write") + " (Tab)";
        String controls = multiFs
                ? appendFilesystemSelector(baseControls, filesystems, fsIndex, innerW)
                : baseControls;
        Theme.put(graphics, innerX, y + 3, innerW, controls, Theme.INK_SECONDARY);
        int chartY = y + 4;
        int chartHeight = compact
                ? Math.max(3, overlayHeight - 5)
                : Math.max(4, overlayHeight - (explainer ? 13 : 10));
        double[] samples = history.samples(range);
        String series = write ? "write" : "read";
        TextColor seriesColor = write ? Theme.SWAP_SERIES : Theme.IO_SERIES;
        LineChart.draw(graphics, x + 1, chartY, overlayWidth - 2, chartHeight,
                filesystem.mountPoint() + " · " + series, "M/s", samples,
                adaptiveMaximum(samples, overlayWidth - 2, 0.1, 0),
                seriesColor, cursor, null, timeLabels(range), Theme.BRAILLE);
        int noteY = chartY + chartHeight;
        if (!compact) {
            drawLegend(graphics, innerX, noteY, innerW, "● " + series, coverage);
            noteY++;
        }
        int detailY = noteY;
        if (cursor >= 0 && cursor < samples.length) {
            String readout = "▸ " + filesystem.mountPoint()
                    + "  " + series + " " + mibRate(mibAt(samples, cursor));
            Theme.put(graphics, innerX, detailY, innerW, readout, Theme.ACCENT);
            detailY++;
        }
        if (!compact && explainer && detailY < y + overlayHeight - 2) {
            Panel.draw(graphics, innerX, detailY, innerW, 3, "What is Disk I/O?");
            Theme.put(graphics, innerX + 1, detailY + 1, innerW - 2,
                    explanation(4), Theme.INK_SECONDARY);
            detailY += 4;
        }
        if (!compact && detailY < y + overlayHeight - 1) {
            Theme.put(graphics, innerX, detailY, innerW,
                    filesystemSummary(filesystem), Theme.INK_SECONDARY);
        }
        Theme.put(graphics, innerX, y + overlayHeight - 1, innerW,
                multiFs
                        ? footerHint(innerW,
                                "←/→ drill-in   ↑↓ disk   Tab read/write   h/l cursor   [ ] range   e explain   Esc close",
                                "←/→ drill  ↑↓ disk  Tab I/O  Esc")
                        : footerHint(innerW,
                                "←/→ drill-in   Tab read/write   h/l cursor   [ ] range   e explain   Esc close",
                                "←/→ drill  Tab I/O  h/l cur  Esc"),
                Theme.INK_MUTED);
    }

    private static int resolveFilesystemIndex(List<FilesystemSeries> filesystems,
            String mountPoint, int selectedFilesystem) {
        if (selectedFilesystem >= 0 && selectedFilesystem < filesystems.size()) {
            return selectedFilesystem;
        }
        if (mountPoint != null) {
            for (int index = 0; index < filesystems.size(); index++) {
                if (filesystems.get(index).mountPoint().equals(mountPoint)) {
                    return index;
                }
            }
        }
        return 0;
    }

    private static String appendFilesystemSelector(String baseControls,
            List<FilesystemSeries> filesystems, int activeIndex, int innerW) {
        int budget = innerW - baseControls.length() - 3;
        if (budget < 10) {
            return baseControls;
        }
        StringBuilder full = new StringBuilder("Disk:");
        for (int index = 0; index < filesystems.size(); index++) {
            String mount = filesystems.get(index).mountPoint();
            full.append(' ').append(index == activeIndex ? "[" + mount + "]" : mount);
        }
        full.append(" (↑↓)");
        if (full.length() <= budget) {
            return baseControls + "   " + full;
        }
        String compact = "Disk " + (activeIndex + 1) + "/" + filesystems.size() + " (↑↓)";
        return compact.length() <= budget ? baseControls + "   " + compact : baseControls;
    }

    private static String activeText(Double utilizationPercent) {
        return utilizationPercent == null ? "n/a" : String.format("%.0f%%", utilizationPercent);
    }

    /**
     * Scales a chart to the data it can actually display: the most recent samples that fit in the plot, never below {@code floor} and optionally capped by {@code ceiling}.
     */
    private static double adaptiveMaximum(double[] samples, int chartWidth,
            double floor, double ceiling) {
        return adaptiveMaximumOver(samples,
                LineChart.sampleCapacity(chartWidth, Theme.BRAILLE), floor, ceiling);
    }

    /**
     * Scales a chart to the most recent {@code visibleSamples} samples, never below {@code floor} and optionally capped by {@code ceiling}.
     */
    private static double adaptiveMaximumOver(double[] samples, int visibleSamples,
            double floor, double ceiling) {
        if (samples == null || samples.length == 0) {
            return floor;
        }
        int visible = Math.min(samples.length, Math.max(1, visibleSamples));
        double peak = 0;
        for (int index = samples.length - visible; index < samples.length; index++) {
            double sample = samples[index];
            if (!Double.isNaN(sample)) {
                peak = Math.max(peak, sample);
            }
        }
        double maximum = Math.max(floor, niceCeiling(peak * 1.1));
        return ceiling > 0 ? Math.min(ceiling, maximum) : maximum;
    }

    private static double[] combinedSamples(FilesystemSeries filesystem,
            TimeSeriesBuffer.Range range) {
        double[] read = filesystem.readHistory().samples(range);
        double[] write = filesystem.writeHistory().samples(range);
        int length = Math.max(read.length, write.length);
        double[] combined = new double[length];
        for (int index = 0; index < length; index++) {
            combined[index] = combinedAt(read, write, index);
        }
        return combined;
    }

    private static double combinedAt(double[] read, double[] write, int index) {
        double readValue = index < read.length ? read[index] : Double.NaN;
        double writeValue = index < write.length ? write[index] : Double.NaN;
        if (Double.isNaN(readValue) && Double.isNaN(writeValue)) {
            return Double.NaN;
        }
        return (Double.isNaN(readValue) ? 0 : readValue)
                + (Double.isNaN(writeValue) ? 0 : writeValue);
    }

    private static String filesystemSummary(FilesystemSeries filesystem) {
        StringBuilder text = new StringBuilder();
        text.append(filesystem.type())
                .append("   used ").append(gib(filesystem.usedBytes()))
                .append(" / ").append(gib(filesystem.totalBytes())).append(" GiB");
        if (filesystem.utilizationPercent() != null) {
            text.append("   active ").append(String.format("%.0f%%", filesystem.utilizationPercent()));
        }
        if (!filesystem.backingDevices().isEmpty()) {
            text.append("   ").append(String.join(", ", filesystem.backingDevices()));
        }
        return text.toString();
    }

    private static Double mibAt(double[] samples, int index) {
        if (index < 0 || index >= samples.length || Double.isNaN(samples[index])) {
            return null;
        }
        return samples[index];
    }

    private static String mibRate(Double mebibytesPerSecond) {
        if (mebibytesPerSecond == null || Double.isNaN(mebibytesPerSecond)) {
            return "n/a";
        }
        return mebibytesPerSecond >= 100
                ? String.format("%.0fM/s", mebibytesPerSecond)
                : String.format("%.1fM/s", mebibytesPerSecond);
    }

    private static Double mib(Double bytesPerSecond) {
        return bytesPerSecond == null ? null : bytesPerSecond / (1024.0 * 1024);
    }

    private static void drawDetailSummary(TextGraphics graphics, DashboardSnapshot snapshot,
            int card, int x, int y, int width, boolean perCore, int selectedCore) {
        String text = switch (card) {
            case 0 ->
                snapshot == null || snapshot.cpu() == null ? "CPU data unavailable"
                : (perCore ? "Per-core selector active" : "Total CPU selected")
                + "  cores " + snapshot.cpu().logicalProcessors()
                + (perCore ? "  active core " + selectedCore : "");
            case 1 ->
                snapshot == null || snapshot.memory() == null ? "Memory data unavailable"
                : "used " + gib(snapshot.memory().usedBytes()) + " / " + gib(snapshot.memory().totalBytes()) + " GiB"
                + "  swap " + gib(snapshot.memory().swapUsedBytes()) + " / " + gib(snapshot.memory().swapTotalBytes());
            case 2 ->
                snapshot == null || snapshot.cpu() == null ? "Temperature data unavailable"
                : "temperature " + temperature(snapshot.cpu().temperatureCelsius());
            case 3 ->
                snapshot == null || snapshot.gpu() == null ? "GPU data unavailable"
                : "devices " + snapshot.gpu().devices().size();
            case 4 ->
                snapshot == null || snapshot.disk() == null ? "Disk data unavailable"
                : "devices " + snapshot.disk().devices().size() + "  filesystems " + snapshot.disk().filesystems().size();
            default ->
                "Metric data unavailable";
        };
        Theme.put(graphics, x, y, width, text, Theme.INK_SECONDARY);
    }

    private static void drawPerCoreGrid(TextGraphics graphics, int x, int y, int width, int height,
            List<TimeSeriesBuffer> histories, TimeSeriesBuffer.Range range,
            int selectedCore, int[] cursors) {
        if (histories.isEmpty()) {
            Theme.put(graphics, x, y, width, "per-core history unavailable", Theme.INK_MUTED);
            return;
        }
        int columns = width >= 160 ? 4 : width >= 80 ? 2 : 1;
        columns = Math.min(columns, histories.size());
        int rows = (histories.size() + columns - 1) / columns;
        if (height < rows * 3) {
            for (int index = 0; index < histories.size() && index < height; index++) {
                String title = "C" + index + (index == selectedCore ? "*" : " ");
                Theme.put(graphics, x, y + index, 3, title, Theme.INK_SECONDARY);
                Sparkline.draw(graphics, x + 3, y + index, Math.max(1, width - 3),
                        histories.get(index).samples(range), Theme.CPU_SERIES, false);
            }
            return;
        }
        int cellWidth = Math.max(8, width / columns);
        int cellHeight = Math.max(3, height / rows);
        for (int index = 0; index < histories.size(); index++) {
            int column = index % columns;
            int row = index / columns;
            int cellX = x + column * cellWidth;
            int cellY = y + row * cellHeight;
            int actualWidth = column == columns - 1 ? width - column * cellWidth : cellWidth - 1;
            int actualHeight = row == rows - 1 ? height - row * cellHeight : cellHeight - 1;
            double[] samples = histories.get(index).samples(range);
            int cursor = index < cursors.length ? cursors[index] : -1;
            String title = "Core " + index + (index == selectedCore ? " *" : "");
            if (actualHeight >= 6) {
                LineChart.draw(graphics, cellX, cellY, actualWidth, actualHeight, title, "%",
                        samples, axisMaximum(0, samples, actualWidth), Theme.CPU_SERIES, cursor,
                        null, timeLabels(range), Theme.BRAILLE);
                if (cursor >= 0 && cursor < samples.length && !Double.isNaN(samples[cursor])) {
                    String readout = "▸ cursor " + formatMetric(samples[cursor], 0);
                    int titleLen = title.length();
                    int avail = actualWidth - titleLen - 1;
                    if (avail >= readout.length()) {
                        Theme.put(graphics, cellX + titleLen + 1, cellY, avail, readout, Theme.ACCENT, true);
                    }
                }
            } else {
                Panel.draw(graphics, cellX, cellY, actualWidth, actualHeight, title);
                Sparkline.draw(graphics, cellX + 1, cellY + 1, actualWidth - 2,
                        samples, Theme.CPU_SERIES, false);
                if (cursor >= 0 && cursor < samples.length && !Double.isNaN(samples[cursor])) {
                    String readout = "▸ cursor " + formatMetric(samples[cursor], 0);
                    int titleLen = title.length() + 2; // Panel has borders
                    int avail = actualWidth - titleLen - 2;
                    if (avail >= readout.length()) {
                        Theme.put(graphics, cellX + titleLen, cellY, avail, readout, Theme.ACCENT, true);
                    }
                }
            }
        }
    }

    /**
     * Legend on the left and the coverage note on the right of the note row.
     */
    private static void drawLegend(TextGraphics graphics, int x, int y, int width,
            String legend, String note) {
        Theme.put(graphics, x, y, width, legend, Theme.INK_SECONDARY);
        if (note == null) {
            return;
        }
        int noteX = Math.max(x + legend.length() + 2, x + width - note.length());
        Theme.put(graphics, noteX, y, Math.max(0, x + width - noteX), note, Theme.INK_MUTED);
    }

    /**
     * X-axis labels for the selected range, newest at the right edge.
     */
    private static String[] timeLabels(TimeSeriesBuffer.Range range) {
        return switch (range) {
            case FIFTEEN_MINUTES ->
                new String[]{"-15m", "-10m", "-5m", "now"};
            case ONE_HOUR ->
                new String[]{"-1h", "-40m", "-20m", "now"};
            case SIX_HOURS ->
                new String[]{"-6h", "-4h", "-2h", "now"};
        };
    }

    private static String legendFor(int card) {
        return switch (card) {
            case 0 ->
                "● total";
            case 1 ->
                "● used   ○ swap";
            case 2 ->
                "● temperature";
            case 3 ->
                "● gpu";
            default ->
                "● disk I/O";
        };
    }

    private static String rangeTabs(TimeSeriesBuffer.Range range) {
        return switch (range) {
            case FIFTEEN_MINUTES ->
                "[15M]  1H  6H";
            case ONE_HOUR ->
                " 15M  [1H]  6H";
            case SIX_HOURS ->
                " 15M   1H  [6H]";
        };
    }

    private static String current(TimeSeriesBuffer history, TimeSeriesBuffer.Range range, int card) {
        if (history == null || Double.isNaN(history.current(range)))
            return "n/a";
        return formatMetric(history.current(range), card);
    }

    private static String formatMetric(double value, int card) {
        if (card == 2)
            return String.format("%.1f°C", value);
        if (card == 4)
            return String.format("%.1fM/s", value);
        return String.format("%.1f%%", value);
    }

    private static double axisMaximum(int card, double[] samples, int chartWidth) {
        return switch (card) {
            case 0, 1, 3 ->
                adaptiveMaximum(samples, chartWidth, 10, 100);
            case 2 ->
                adaptiveMaximum(samples, chartWidth, 10, 0);
            default ->
                adaptiveMaximum(samples, chartWidth, 0.1, 0);
        };
    }

    private static double niceCeiling(double target) {
        double safeTarget = Math.max(1e-9, target);
        double scale = Math.pow(10, Math.floor(Math.log10(safeTarget)));
        for (double step : new double[]{1, 1.5, 2, 2.5, 3, 4, 5, 6, 8, 10})
            if (step * scale >= safeTarget)
                return step * scale;
        return 10 * scale;
    }

    private static String explanation(int card) {
        return switch (card) {
            case 0 ->
                "Share of time processors were busy, averaged over all cores.";
            case 1 ->
                "Memory currently occupied by applications and the operating system.";
            case 2 ->
                "The measure of how hot a computer's central processing unit gets while running.";
            case 3 ->
                "Share of time the graphics processor is busy.";
            case 4 ->
                "Combined read and write throughput from I/O devices.";
            default ->
                "Metric explanation unavailable.";
        };
    }

    private static TimeSeriesBuffer historyForCard(int selectedCard,
            TimeSeriesBuffer cpuHistory, TimeSeriesBuffer memoryHistory,
            TimeSeriesBuffer temperatureHistory, TimeSeriesBuffer gpuHistory,
            TimeSeriesBuffer diskIoHistory) {
        return switch (selectedCard) {
            case 0 ->
                cpuHistory;
            case 1 ->
                memoryHistory;
            case 2 ->
                temperatureHistory;
            case 3 ->
                gpuHistory;
            case 4 ->
                diskIoHistory;
            default ->
                null;
        };
    }

    private static String aggregate(TimeSeriesBuffer history, int selectedCard,
            TimeSeriesBuffer.Range range, boolean peak) {
        if (history == null)
            return "n/a";
        double value = peak ? history.peak(range) : history.average(range);
        if (Double.isNaN(value))
            return "n/a";
        return formatMetric(value, selectedCard);
    }

    private static void drawHeader(TextGraphics graphics, int width, DashboardSnapshot snapshot) {
        String hostname = snapshot == null || snapshot.host() == null
                ? "host" : snapshot.host().hostname();
        String status = snapshot == null ? "○" : "●";
        String clock = snapshot == null ? "--:--:--" : CLOCK.format(snapshot.sampledAt());
        String uptime = snapshot == null || snapshot.host() == null
                ? "up --" : uptime(snapshot.host().uptimeSeconds());
        String right = width >= 100
                ? status + "  " + uptime + "  LIVE • " + clock
                : status + "  " + uptime + "  LIVE " + clock;
        int rightX = Math.max(1, width - right.length() - 1);
        Theme.put(graphics, 1, 0, Math.max(0, rightX - 2),
                "THYME  " + hostname, Theme.INK, true);
        Theme.put(graphics, rightX, 0, Math.max(0, width - rightX - 1), right,
                snapshot == null ? Theme.WARM : healthColor(snapshot));
        graphics.setForegroundColor(Theme.HAIRLINE);
        graphics.drawLine(0, 1, width - 1, 1, Theme.H);
    }

    /**
     * Short uptime label: {@code up 12d 4h}, {@code up 4h 20m} or {@code up 12m}.
     */
    private static String uptime(long seconds) {
        long days = seconds / 86400;
        long hours = seconds % 86400 / 3600;
        long minutes = seconds % 3600 / 60;
        if (days > 0) {
            return "up " + days + "d" + (hours > 0 ? " " + hours + "h" : "");
        }
        if (hours > 0) {
            return "up " + hours + "h" + (minutes > 0 ? " " + minutes + "m" : "");
        }
        return "up " + Math.max(1, minutes) + "m";
    }

    private static void drawFooter(TextGraphics graphics, int width, int y, boolean filteringEvents, String eventFilter) {
        graphics.setForegroundColor(Theme.HAIRLINE);
        graphics.drawLine(0, y, width - 1, y, Theme.H);
        Dock.draw(graphics, 1, y, width - 2, DOCK_LABELS, 0);

        String hints;
        if (filteringEvents) {
            hints = "Filter events: " + (eventFilter == null ? "" : eventFilter) + "_   (Enter apply · Esc cancel)";
        } else if (eventFilter != null && !eventFilter.isEmpty()) {
            hints = "Events: " + eventFilter + " (/ edit · Esc clear)   ←↑↓→ select  Click/Enter detail";
        } else {
            hints = footerHint(Math.max(0, width - 26),
                    "←↑↓→ select  Click/Enter detail  / filter events  ? help  q quit",
                    "←↑↓→ select  Enter detail  ? help  q quit");
        }
        int hintsX = Math.max(1, width - hints.length() - 1);
        if (hintsX > 25) {
            TextColor hintColor = filteringEvents ? Theme.ACCENT : Theme.INK_MUTED;
            Theme.put(graphics, hintsX, y, hints.length(), hints, hintColor);
        }
    }

    private record TileSpec(
            String label,
            String valueText,
            TextColor valueColor,
            String unit,
            TextColor seriesColor,
            double[] samples,
            double maxValue,
            String tileError,
            String emptyMessage) {
    }

    private static void drawCollageTile(TextGraphics graphics, TileRect tile, OverviewCard card,
            DashboardSnapshot snapshot, TimeSeriesBuffer cpuHistory, TimeSeriesBuffer memoryHistory,
            TimeSeriesBuffer temperatureHistory, TimeSeriesBuffer gpuHistory,
            TimeSeriesBuffer diskIoHistory, List<FilesystemSeries> filesystems,
            String error, boolean selected) {
        int x = tile.x();
        int y = tile.y();
        int width = tile.width();
        int height = tile.height();
        graphics.setBackgroundColor(Theme.PAGE);
        Panel.draw(graphics, x, y, width, height, null, selected ? Theme.ACCENT : Theme.HAIRLINE);
        int ix = Panel.interiorX(x);
        int iy = Panel.interiorY(y);
        int iw = Panel.interiorW(width);
        int ih = Panel.interiorH(height);
        TileSpec spec = buildTileSpec(card, snapshot, cpuHistory, memoryHistory,
                temperatureHistory, gpuHistory, diskIoHistory, filesystems, error, iw, ih);
        drawTileTitle(graphics, x, y, width, spec.label(), spec.valueText(), spec.valueColor(), selected);
        if (iw < 2 || ih < 1) {
            return;
        }
        if (spec.tileError() != null) {
            Theme.put(graphics, ix, iy, iw, spec.tileError(), Theme.CRITICAL);
            return;
        }
        if (!hasFiniteSample(spec.samples())) {
            Theme.put(graphics, ix, iy + ih / 2, iw, spec.emptyMessage(), Theme.INK_MUTED);
            return;
        }
        LineChart.drawCompact(graphics, ix, iy, iw, ih, spec.unit(), spec.samples(),
                spec.maxValue(), spec.seriesColor(),
                timeLabels(TimeSeriesBuffer.Range.FIFTEEN_MINUTES), Theme.BRAILLE);
    }

    private static TileSpec buildTileSpec(OverviewCard card, DashboardSnapshot snapshot,
            TimeSeriesBuffer cpuHistory, TimeSeriesBuffer memoryHistory,
            TimeSeriesBuffer temperatureHistory, TimeSeriesBuffer gpuHistory,
            TimeSeriesBuffer diskIoHistory, List<FilesystemSeries> filesystems,
            String error, int interiorWidth, int interiorHeight) {
        TimeSeriesBuffer.Range range = TimeSeriesBuffer.Range.FIFTEEN_MINUTES;
        int capacity = LineChart.compactSampleCapacity(interiorWidth, interiorHeight, Theme.BRAILLE);
        String waiting = interiorWidth >= 20 ? "waiting for samples" : "waiting";
        return switch (card.kind()) {
            case CPU -> {
                CpuSnapshot cpu = snapshot == null ? null : snapshot.cpu();
                Double usage = cpu == null ? null : cpu.totalUsagePercent();
                double[] samples = cpuHistory.samples(range);
                yield new TileSpec("CPU", percent(usage), Theme.utilizationColor(usage),
                        "%", Theme.CPU_SERIES, samples,
                        adaptiveMaximumOver(samples, capacity, 10, 100),
                        tileError(snapshot, error, "CPU", cpu == null, true), waiting);
            }
            case RAM -> {
                var memory = snapshot == null ? null : snapshot.memory();
                Double used = memory == null ? null : ratio(memory.usedBytes(), memory.totalBytes());
                double[] samples = memoryHistory.samples(range);
                yield new TileSpec("RAM", percent(used), Theme.utilizationColor(used),
                        "%", Theme.MEMORY_SERIES, samples,
                        adaptiveMaximumOver(samples, capacity, 10, 100),
                        tileError(snapshot, error, "RAM", memory == null, false), waiting);
            }
            case TEMPERATURE -> {
                CpuSnapshot cpu = snapshot == null ? null : snapshot.cpu();
                Double celsius = cpu == null ? null : cpu.temperatureCelsius();
                double[] samples = temperatureHistory.samples(range);
                String empty = cpu != null && celsius == null
                        ? (interiorWidth >= 20 ? "sensor unavailable" : "no sensor")
                        : waiting;
                yield new TileSpec("Temp", temperature(celsius), Theme.temperatureColor(celsius),
                        "°C", Theme.TEMP_SERIES, samples,
                        adaptiveMaximumOver(samples, capacity, 10, 0),
                        tileError(snapshot, error, "CPU", cpu == null, false), empty);
            }
            case GPU -> {
                GpuReading reading = snapshot == null ? null : snapshot.gpu();
                GpuSnapshot gpu = firstGpu(reading);
                Double util = gpu == null ? null : gpu.utilizationPercent();
                double[] samples = gpuHistory.samples(range);
                String empty = reading != null && reading.devices().isEmpty()
                        ? (interiorWidth >= 16 ? "no GPU detected" : "no GPU")
                        : waiting;
                yield new TileSpec("GPU", percent(util), Theme.utilizationColor(util),
                        "%", Theme.GPU_SERIES, samples,
                        adaptiveMaximumOver(samples, capacity, 10, 100),
                        tileError(snapshot, error, "GPU", reading == null || reading.devices().isEmpty(), false),
                        empty);
            }
            case FILESYSTEM -> {
                FilesystemSeries fs = findFilesystem(filesystems, card.mountPoint());
                Double combined = fs == null ? null : combinedMib(fs);
                double[] samples = fs == null ? new double[0] : combinedSamples(fs, range);
                String empty = snapshot != null && snapshot.disk() != null ? "no I/O data" : waiting;
                yield new TileSpec("Disk " + card.mountPoint(),
                        combined == null ? "n/a" : mibRate(combined),
                        combined == null ? Theme.INK_MUTED : Theme.INK,
                        "M/s", Theme.IO_SERIES, samples,
                        adaptiveMaximumOver(samples, capacity, 0.1, 0),
                        tileError(snapshot, error, "Disks",
                                fs == null && (snapshot == null || snapshot.disk() == null), false),
                        empty);
            }
            case DISK_TOTAL -> {
                Double total = totalFilesystemIo(snapshot == null ? null : snapshot.disk());
                double[] samples = diskIoHistory.samples(range);
                String empty = snapshot != null && snapshot.disk() != null ? "no I/O data" : waiting;
                yield new TileSpec("Disk I/O",
                        total == null ? "n/a" : rate(total),
                        total == null ? Theme.INK_MUTED : Theme.INK,
                        "M/s", Theme.IO_SERIES, samples,
                        adaptiveMaximumOver(samples, capacity, 0.1, 0),
                        tileError(snapshot, error, "Disks",
                                snapshot == null || snapshot.disk() == null, false),
                        empty);
            }
        };
    }

    private static String tileError(DashboardSnapshot snapshot, String legacyError,
            String providerKey, boolean missingData, boolean acceptLegacyError) {
        if (snapshot != null && !snapshot.errors().isEmpty()) {
            String err = snapshot.errors().get(providerKey);
            return err != null && missingData ? err : null;
        }
        return acceptLegacyError ? legacyError : null;
    }

    private static void drawTileTitle(TextGraphics graphics, int x, int y, int width,
            String label, String valueText, TextColor valueColor, boolean selected) {
        int budget = width - 4;
        if (budget <= 0) {
            return;
        }
        String prefix = selected ? " ▸ " : " ";
        String sep = " · ";
        String arrow = " ›";
        String trailing = " ";
        int fixed = prefix.length() + sep.length() + valueText.length() + arrow.length() + trailing.length();
        if (fixed + 3 > budget) {
            String compact = Theme.clipToWidth(prefix + label + sep + valueText + arrow + trailing, budget);
            Theme.put(graphics, x + 2, y, budget, compact,
                    selected ? Theme.ACCENT : Theme.INK_SECONDARY, true);
            return;
        }
        String fittedLabel = fixed + label.length() <= budget
                ? label
                : Theme.clipToWidth(label, budget - fixed);
        int col = x + 2;
        Theme.put(graphics, col, y, prefix.length(), prefix,
                selected ? Theme.ACCENT : Theme.INK_SECONDARY, true);
        col += prefix.length();
        Theme.put(graphics, col, y, fittedLabel.length(), fittedLabel,
                selected ? Theme.ACCENT : Theme.INK_SECONDARY, true);
        col += fittedLabel.length();
        Theme.put(graphics, col, y, sep.length(), sep, Theme.INK_MUTED);
        col += sep.length();
        Theme.put(graphics, col, y, valueText.length(), valueText, valueColor, true);
        col += valueText.length();
        Theme.put(graphics, col, y, arrow.length(), arrow,
                selected ? Theme.ACCENT : Theme.INK_MUTED, selected);
        col += arrow.length();
        Theme.put(graphics, col, y, trailing.length(), trailing, Theme.INK_MUTED);
    }

    private static boolean hasFiniteSample(double[] samples) {
        if (samples == null) {
            return false;
        }
        for (double sample : samples) {
            if (Double.isFinite(sample)) {
                return true;
            }
        }
        return false;
    }

    private static FilesystemSeries findFilesystem(List<FilesystemSeries> filesystems, String mountPoint) {
        for (FilesystemSeries filesystem : filesystems) {
            if (filesystem.mountPoint().equals(mountPoint)) {
                return filesystem;
            }
        }
        return null;
    }

    private static void drawEvents(TextGraphics graphics, int x, int y, int width, int height,
            EventLogBuffer eventLog, String eventFilter) {
        List<EventLog.Entry> entries = new ArrayList<>();
        String q = (eventFilter == null || eventFilter.isBlank()) ? null : eventFilter.toLowerCase(Locale.ROOT);
        for (EventLogBuffer.Event event : eventLog.snapshot()) {
            if (q == null || event.message().toLowerCase(Locale.ROOT).contains(q)
                    || event.severity().name().toLowerCase(Locale.ROOT).contains(q)) {
                entries.add(new EventLog.Entry(CLOCK.format(event.time()), severityColor(event.severity()), event.message()));
            }
        }
        String title = (q == null)
                ? "Failed events • " + eventLog.size()
                : "Failed events • " + entries.size() + "/" + eventLog.size() + " (filtered)";
        EventLog.draw(graphics, x, y, width, height, title, entries);
    }

    private static String chartUnit(int selectedCard) {
        return switch (selectedCard) {
            case 2 ->
                "°C";
            case 4 ->
                "M/s";
            default ->
                "%";
        };
    }

    private static TextColor seriesColor(int selectedCard) {
        return switch (selectedCard) {
            case 0 ->
                Theme.CPU_SERIES;
            case 1 ->
                Theme.MEMORY_SERIES;
            case 2 ->
                Theme.TEMP_SERIES;
            case 3 ->
                Theme.GPU_SERIES;
            case 4 ->
                Theme.IO_SERIES;
            default ->
                Theme.PRIMARY;
        };
    }

    private static TextColor healthColor(DashboardSnapshot snapshot) {
        if (!snapshot.errors().isEmpty())
            return Theme.CRITICAL;
        CpuSnapshot cpu = snapshot.cpu();
        return Theme.utilizationColor(cpu == null ? null : cpu.totalUsagePercent());
    }

    private static GpuSnapshot firstGpu(GpuReading gpu) {
        return gpu == null || gpu.devices().isEmpty() ? null : gpu.devices().get(0);
    }

    private static Double totalFilesystemIo(DiskSnapshot disk) {
        if (disk == null || disk.filesystems().isEmpty()) {
            return null;
        }
        double total = 0;
        boolean available = false;
        for (DiskSnapshot.Filesystem filesystem : disk.filesystems()) {
            boolean hasRead = filesystem.readBytesPerSecond() != null;
            boolean hasWrite = filesystem.writeBytesPerSecond() != null;
            if (hasRead || hasWrite) {
                total += (hasRead ? filesystem.readBytesPerSecond() : 0)
                        + (hasWrite ? filesystem.writeBytesPerSecond() : 0);
                available = true;
            }
        }
        return available ? total : null;
    }

    private static Double combinedMib(FilesystemSeries filesystem) {
        Double read = mib(filesystem.readBytesPerSecond());
        Double write = mib(filesystem.writeBytesPerSecond());
        if (read == null && write == null) {
            return null;
        }
        return (read == null ? 0 : read) + (write == null ? 0 : write);
    }

    private static String percent(Double value) {
        return value == null ? "n/a" : String.format("%.0f%%", value);
    }

    private static String temperature(Double value) {
        return value == null ? "n/a" : String.format("%.0f°C", value);
    }

    private static String gib(long bytes) {
        return String.format("%.1f", bytes / (1024.0 * 1024 * 1024));
    }

    private static String rate(Double value) {
        return value == null ? "n/a" : String.format("%.1fM/s", value / (1024 * 1024));
    }

    private static double ratio(long used, long total) {
        return total == 0 ? 0 : used * 100.0 / total;
    }

    private static TextColor severityColor(EventLogBuffer.Severity severity) {
        return switch (severity) {
            case GOOD ->
                Theme.GOOD;
            case WARM ->
                Theme.WARM;
            case CRITICAL ->
                Theme.CRITICAL;
        };
    }
}
