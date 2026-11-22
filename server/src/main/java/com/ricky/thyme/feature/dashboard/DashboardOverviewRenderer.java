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
import com.ricky.thyme.ui.widgets.StatCard;
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

    /** Draws the overview/detail with an explicit swap history. */
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
        if (details) {
            drawOverview(graphics, size, snapshot, cpuHistory, memoryHistory,
                    temperatureHistory, gpuHistory, diskIoHistory, eventLog, error,
                    selectedCard, List.of(), "");
            drawDetail(graphics, width, footerY, snapshot, cpuHistory, memoryHistory,
                    swapHistory, temperatureHistory, gpuHistory, diskIoHistory, selectedCard,
                    TimeSeriesBuffer.Range.FIFTEEN_MINUTES, -1, false, true, List.of(), 0,
                    new int[0], List.of(), false);
            return;
        }
        drawOverview(graphics, size, snapshot, cpuHistory, memoryHistory,
                temperatureHistory, gpuHistory, diskIoHistory, eventLog, error,
                selectedCard, List.of(), "");
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
        int footerY = height - 1;
        int contentY = 2;
        int contentHeight = footerY - contentY;
        if (width < 80) {
            drawCompact(graphics, width, contentY, contentHeight, snapshot, cpuHistory, error);
            return;
        }
        int cardsHeight = cardsHeight(width, OverviewCard.cards(snapshot).size());
        drawCards(graphics, width, contentY, snapshot, cpuHistory, memoryHistory,
                temperatureHistory, gpuHistory, diskIoHistory, filesystems, selectedCard);
        int bodyY = contentY + cardsHeight + 1;
        int bodyHeight = Math.max(4, footerY - bodyY);
        if (width >= 120) {
            int eventsWidth = Math.max(28, width / 3);
            drawCharts(graphics, 0, bodyY, width - eventsWidth - 1, bodyHeight,
                    cpuHistory, memoryHistory, error);
            drawEvents(graphics, width - eventsWidth, bodyY, eventsWidth, bodyHeight, eventLog, eventFilter);
        } else {
            drawCharts(graphics, 0, bodyY, width, bodyHeight, cpuHistory, memoryHistory, error);
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
        cpuHistory = safeHistory(cpuHistory);
        memoryHistory = safeHistory(memoryHistory);
        swapHistory = safeHistory(swapHistory);
        temperatureHistory = safeHistory(temperatureHistory);
        gpuHistory = safeHistory(gpuHistory);
        diskIoHistory = safeHistory(diskIoHistory);
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
                    filesystems, filesystemWrite);
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
            List<FilesystemSeries> filesystems, boolean filesystemWrite) {
        OverviewCard card = cardAt(snapshot, selectedCard);
        if (card.filesystem()) {
            drawFilesystemDetail(graphics, width, height, filesystems, card,
                    range, cursor, explainer, filesystemWrite);
            return;
        }
        String label = card.detailLabel();
        // Compact tier (< 100 cols): overlay spans full width so the overview
        // does not bleed through at the sides and the footer dock is not clipped.
        boolean fullWidth = width < 100;
        int overlayWidth = fullWidth ? width
                : Math.max(44, Math.min(width - 4, (int) Math.round(width * 0.85)));
        int overlayHeight = Math.max(11, Math.min(height - 1, (int) Math.round(height * 0.90)));
        int x = fullWidth ? 0 : (width - overlayWidth) / 2;
        int y = Math.max(2, (height - overlayHeight) / 2 + 1);
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
        double[] swapSamples = selectedCard == 1 ? swapHistory.samples(range) : new double[0];
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

    /** Full key hint when it fits, otherwise a compact variant. */
    private static String footerHint(int width, String full, String compact) {
        return width >= full.length() ? full : compact;
    }

    private static OverviewCard cardAt(DashboardSnapshot snapshot, int index) {
        List<OverviewCard> cards = OverviewCard.cards(snapshot);
        return cards.get(Math.max(0, Math.min(index, cards.size() - 1)));
    }

    /**
     * Draws the drill-in for one filesystem card.  Each filesystem has its own
     * detail overlay; {@code write} selects the write series, otherwise the
     * read series is shown.
     */
    private static void drawFilesystemDetail(TextGraphics graphics, int width, int height,
            List<FilesystemSeries> filesystems, OverviewCard card,
            TimeSeriesBuffer.Range range, int cursor, boolean explainer, boolean write) {
        // Compact tier (< 100 cols): overlay spans full width (R10 / spec §4).
        boolean fullWidth = width < 100;
        int overlayWidth = fullWidth ? width
                : Math.max(44, Math.min(width - 4, (int) Math.round(width * 0.85)));
        int overlayHeight = Math.max(11, Math.min(height - 1, (int) Math.round(height * 0.90)));
        int x = fullWidth ? 0 : (width - overlayWidth) / 2;
        int y = Math.max(2, (height - overlayHeight) / 2 + 1);
        graphics.setBackgroundColor(Theme.SURFACE);
        // Full-width overlays clear the whole area below the header to avoid
        // overview residue (panel borders) showing outside the overlay rect.
        int fillHeight = fullWidth ? height - y : overlayHeight;
        graphics.fillRectangle(new TerminalPosition(x, y), new TerminalSize(overlayWidth, fillHeight), ' ');
        Panel.draw(graphics, x, y, overlayWidth, overlayHeight,
                "Disk I/O  ·  " + card.detailLabel() + "  ·  Esc close");
        int innerX = x + 2;
        int innerW = overlayWidth - 4;
        FilesystemSeries filesystem = findFilesystem(filesystems, card.mountPoint());
        if (filesystem == null) {
            Theme.put(graphics, innerX, y + 2, innerW,
                    "Filesystem " + card.detailLabel() + " is no longer available", Theme.INK_MUTED);
            Theme.put(graphics, innerX, y + overlayHeight - 1, innerW,
                    "Esc close", Theme.INK_MUTED);
            return;
        }
        Theme.put(graphics, innerX, y + 2, innerW,
                "Read " + mibRate(mib(filesystem.readBytesPerSecond()))
                + "   Write " + mibRate(mib(filesystem.writeBytesPerSecond()))
                + "   Active " + activeText(filesystem.utilizationPercent()), Theme.INK);
        TimeSeriesBuffer history = write ? filesystem.writeHistory() : filesystem.readHistory();
        String coverage = history.coverageNote(range);
        boolean compact = overlayHeight < 20;
        Theme.put(graphics, innerX, y + 3, innerW,
                rangeTabs(range)
                + (compact && coverage != null ? "  " + coverage : "")
                + "   I/O: " + (write ? "Read [Write]" : "[Read] Write") + " (↑↓)",
                Theme.INK_SECONDARY);
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
                footerHint(innerW,
                        "←/→ drill-in   ↑↓ read/write   h/l cursor   [ ] range   e explain   Esc close",
                        "←/→ drill  ↑↓ I/O  h/l cur  Esc"),
                Theme.INK_MUTED);
    }

    private static String activeText(Double utilizationPercent) {
        return utilizationPercent == null ? "n/a" : String.format("%.0f%%", utilizationPercent);
    }

    /**
     * Scales a chart to the data it can actually display: the most recent
     * samples that fit in the plot, never below {@code floor} and optionally
     * capped by {@code ceiling}.
     */
    private static double adaptiveMaximum(double[] samples, int chartWidth,
            double floor, double ceiling) {
        return adaptiveMaximumOver(samples,
                LineChart.sampleCapacity(chartWidth, Theme.BRAILLE), floor, ceiling);
    }

    /**
     * Scales a chart to the most recent {@code visibleSamples} samples, never
     * below {@code floor} and optionally capped by {@code ceiling}.
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

    private static double[] normalize(double[] samples, double max) {
        double safeMax = max > 0 ? max : 1;
        double[] normalized = new double[samples.length];
        for (int index = 0; index < samples.length; index++) {
            normalized[index] = Double.isNaN(samples[index])
                    ? Double.NaN : Math.min(100, samples[index] / safeMax * 100);
        }
        return normalized;
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

    private static String mibShort(Double bytesPerSecond) {
        return bytesPerSecond == null
                ? "n/a" : String.format("%.1f", bytesPerSecond / (1024.0 * 1024));
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

    /** Legend on the left and the coverage note on the right of the note row. */
    private static void drawLegend(TextGraphics graphics, int x, int y, int width,
            String legend, String note) {
        Theme.put(graphics, x, y, width, legend, Theme.INK_SECONDARY);
        if (note == null) {
            return;
        }
        int noteX = Math.max(x + legend.length() + 2, x + width - note.length());
        Theme.put(graphics, noteX, y, Math.max(0, x + width - noteX), note, Theme.INK_MUTED);
    }

    /** X-axis labels for the selected range, newest at the right edge. */
    private static String[] timeLabels(TimeSeriesBuffer.Range range) {
        return switch (range) {
            case FIFTEEN_MINUTES -> new String[]{"-15m", "-10m", "-5m", "now"};
            case ONE_HOUR -> new String[]{"-1h", "-40m", "-20m", "now"};
            case SIX_HOURS -> new String[]{"-6h", "-4h", "-2h", "now"};
        };
    }

    private static String legendFor(int card) {
        return switch (card) {
            case 0 -> "● total";
            case 1 -> "● used   ○ swap";
            case 2 -> "● temperature";
            case 3 -> "● gpu";
            default -> "● disk I/O";
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
            case 0, 1, 3 -> adaptiveMaximum(samples, chartWidth, 10, 100);
            case 2 -> adaptiveMaximum(samples, chartWidth, 10, 0);
            default -> adaptiveMaximum(samples, chartWidth, 0.1, 0);
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
                "The latest reported thermal sensor value.";
            case 3 ->
                "Work currently scheduled on the graphics device.";
            case 4 ->
                "Combined read and write throughput from sampled devices.";
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

    /** Short uptime label: {@code up 12d 4h}, {@code up 4h 20m} or {@code up 12m}. */
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
            hints = "Events: " + eventFilter + " (/ edit · Esc clear)   ↑↓ select  Enter detail";
        } else {
            hints = "↑↓ select  Enter detail  / filter events  ? help  q quit";
        }
        int hintsX = Math.max(1, width - hints.length() - 1);
        if (hintsX > 25) {
            TextColor hintColor = filteringEvents ? Theme.ACCENT : Theme.INK_MUTED;
            Theme.put(graphics, hintsX, y, hints.length(), hints, hintColor);
        }
    }

    private static void drawCompact(TextGraphics graphics, int width, int y, int height,
            DashboardSnapshot snapshot, TimeSeriesBuffer cpuHistory, String error) {
        String[] values = compactValues(snapshot);
        Theme.put(graphics, 1, y, width - 2, String.join("  ", values), Theme.INK_SECONDARY);
        int chartY = y + 2;
        int chartHeight = Math.max(4, height - 2);
        Panel.draw(graphics, 0, chartY, width, chartHeight, "CPU / RAM • 15M");
        if (error != null) {
            Theme.put(graphics, 2, chartY + 2, width - 4, error, Theme.CRITICAL);
        } else {
            double[] samples = cpuHistory.samples(TimeSeriesBuffer.Range.FIFTEEN_MINUTES);
            LineChart.draw(graphics, 1, chartY + 1, width - 2, chartHeight - 2,
                    "CPU / RAM", "%", samples, axisMaximum(0, samples, width - 2),
                    Theme.CPU_SERIES, -1,
                    cpuHistory.coverageNote(TimeSeriesBuffer.Range.FIFTEEN_MINUTES),
                    timeLabels(TimeSeriesBuffer.Range.FIFTEEN_MINUTES), Theme.BRAILLE);
        }
    }

    /** Height of one KPI card row. */
    private static final int CARD_HEIGHT = 5;

    /** Vertical distance between two KPI card rows, including the gap. */
    private static final int CARD_STRIDE = 6;

    private static int cardColumns(int width, int cardCount) {
        int maximum = width >= 160 ? 7 : width >= 120 ? 5 : width >= 80 ? 4 : 3;
        return Math.max(1, Math.min(cardCount, maximum));
    }

    private static int cardsHeight(int width, int cardCount) {
        int columns = cardColumns(width, cardCount);
        int rows = Math.max(1, (cardCount + columns - 1) / columns);
        return rows * CARD_STRIDE - 1;
    }

    private static void drawCards(TextGraphics graphics, int width, int y,
            DashboardSnapshot snapshot, TimeSeriesBuffer cpuHistory, TimeSeriesBuffer memoryHistory,
            TimeSeriesBuffer temperatureHistory, TimeSeriesBuffer gpuHistory,
            TimeSeriesBuffer diskIoHistory, List<FilesystemSeries> filesystems, int selectedCard) {
        List<OverviewCard> cards = OverviewCard.cards(snapshot);
        int columns = cardColumns(width, cards.size());
        int gap = 1;
        int cardWidth = (width - (columns - 1) * gap) / columns;
        for (int index = 0; index < cards.size(); index++) {
            int row = index / columns;
            int column = index % columns;
            int x = column * (cardWidth + gap);
            int cardY = y + row * CARD_STRIDE;
            drawCard(graphics, cards.get(index), x, cardY, cardWidth, snapshot,
                    cpuHistory, memoryHistory, temperatureHistory, gpuHistory, diskIoHistory,
                    filesystems, index == selectedCard);
        }
    }

    private static void drawCard(TextGraphics graphics, OverviewCard card, int x, int y, int width,
            DashboardSnapshot snapshot, TimeSeriesBuffer cpuHistory, TimeSeriesBuffer memoryHistory,
            TimeSeriesBuffer temperatureHistory, TimeSeriesBuffer gpuHistory,
            TimeSeriesBuffer diskIoHistory, List<FilesystemSeries> filesystems,
            boolean selected) {
        TimeSeriesBuffer.Range fifteenMinutes = TimeSeriesBuffer.Range.FIFTEEN_MINUTES;
        String label = card.label();
        String value;
        TextColor valueColor;
        double[] history;
        String caption;
        TextColor sparkColor;
        switch (card.kind()) {
            case CPU -> {
                CpuSnapshot cpu = snapshot == null ? null : snapshot.cpu();
                value = percent(cpu == null ? null : cpu.totalUsagePercent());
                valueColor = Theme.utilizationColor(cpu == null ? null : cpu.totalUsagePercent());
                double[] samples = cpuHistory.samples(fifteenMinutes);
                history = normalize(samples, adaptiveMaximumOver(samples, width, 10, 100));
                caption = "current";
                sparkColor = Theme.CPU_SERIES;
            }
            case RAM -> {
                Double used = snapshot == null || snapshot.memory() == null ? null
                        : ratio(snapshot.memory().usedBytes(), snapshot.memory().totalBytes());
                value = percent(used);
                valueColor = Theme.utilizationColor(used);
                double[] samples = memoryHistory.samples(fifteenMinutes);
                history = normalize(samples, adaptiveMaximumOver(samples, width, 10, 100));
                caption = "current";
                sparkColor = Theme.MEMORY_SERIES;
            }
            case TEMPERATURE -> {
                CpuSnapshot cpu = snapshot == null ? null : snapshot.cpu();
                Double temperature = cpu == null ? null : cpu.temperatureCelsius();
                value = temperature(temperature);
                valueColor = Theme.temperatureColor(temperature);
                double[] samples = temperatureHistory.samples(fifteenMinutes);
                history = normalize(samples, adaptiveMaximumOver(samples, width, 10, 0));
                caption = "current";
                sparkColor = Theme.INK_SECONDARY;
            }
            case GPU -> {
                GpuSnapshot gpu = firstGpu(snapshot == null ? null : snapshot.gpu());
                Double utilization = gpu == null ? null : gpu.utilizationPercent();
                value = percent(utilization);
                valueColor = Theme.utilizationColor(utilization);
                double[] samples = gpuHistory.samples(fifteenMinutes);
                history = normalize(samples, adaptiveMaximumOver(samples, width, 10, 100));
                caption = "latest sample";
                sparkColor = Theme.INK_SECONDARY;
            }
            case FILESYSTEM -> {
                FilesystemSeries filesystem = findFilesystem(filesystems, card.mountPoint());
                Double combined = filesystem == null ? null : combinedMib(filesystem);
                value = combined == null ? "n/a" : mibRate(combined);
                valueColor = Theme.INK;
                double[] samples = filesystem == null
                        ? new double[0] : combinedSamples(filesystem, fifteenMinutes);
                history = normalize(samples, adaptiveMaximumOver(samples, width, 0.1, 0));
                caption = filesystem == null ? "n/a"
                        : "R " + mibShort(filesystem.readBytesPerSecond())
                        + " W " + mibShort(filesystem.writeBytesPerSecond()) + " M/s";
                sparkColor = Theme.IO_SERIES;
            }
            default -> {
                Double total = totalFilesystemIo(snapshot == null ? null : snapshot.disk());
                value = total == null ? "n/a" : rate(total);
                valueColor = Theme.INK;
                double[] samples = diskIoHistory.samples(fifteenMinutes);
                history = normalize(samples, adaptiveMaximumOver(samples, width, 0.1, 0));
                caption = "latest sample";
                sparkColor = Theme.IO_SERIES;
            }
        }
        StatCard.draw(graphics, x, y, width, CARD_HEIGHT, label, value, valueColor, history,
                caption, true, selected, sparkColor);
    }

    private static FilesystemSeries findFilesystem(List<FilesystemSeries> filesystems, String mountPoint) {
        for (FilesystemSeries filesystem : filesystems) {
            if (filesystem.mountPoint().equals(mountPoint)) {
                return filesystem;
            }
        }
        return null;
    }

    private static void drawCharts(TextGraphics graphics, int x, int y, int width, int height,
            TimeSeriesBuffer cpuHistory, TimeSeriesBuffer memoryHistory, String error) {
        if (width < 20 || height < 3)
            return;
        // When height is too tight to split (< 8 rows), show a single CPU panel.
        // The spec §C (80x24) keeps both panels; we split whenever there is room.
        boolean split = height >= 8;
        int firstHeight = split ? Math.max(4, height / 2) : height;
        Panel.draw(graphics, x, y, width, firstHeight, split ? "CPU • 15M" : "CPU / RAM • 15M");
        if (error != null) {
            Theme.put(graphics, x + 2, y + 2, width - 4, error, Theme.CRITICAL);
        } else {
            boolean legend = firstHeight >= 9;
            int chartHeight = firstHeight - 2 - (legend ? 1 : 0);
            double[] samples = cpuHistory.samples(TimeSeriesBuffer.Range.FIFTEEN_MINUTES);
            String coverage = cpuHistory.coverageNote(TimeSeriesBuffer.Range.FIFTEEN_MINUTES);
            LineChart.draw(graphics, x + 1, y + 1, width - 2, chartHeight, "CPU", "%",
                    samples, axisMaximum(0, samples, width - 2),
                    Theme.CPU_SERIES, -1, legend ? null : coverage,
                    timeLabels(TimeSeriesBuffer.Range.FIFTEEN_MINUTES), Theme.BRAILLE);
            if (legend) {
                drawLegend(graphics, x + 2, y + 1 + chartHeight, width - 4, "● total", coverage);
            }
        }
        int secondY = y + firstHeight + 1;
        int secondHeight = height - firstHeight - 1;
        if (split && secondHeight >= 3) {
            Panel.draw(graphics, x, secondY, width, secondHeight, "RAM • 15M");
            boolean legend = secondHeight >= 9;
            int chartHeight = secondHeight - 2 - (legend ? 1 : 0);
            double[] samples = memoryHistory.samples(TimeSeriesBuffer.Range.FIFTEEN_MINUTES);
            String coverage = memoryHistory.coverageNote(TimeSeriesBuffer.Range.FIFTEEN_MINUTES);
            LineChart.draw(graphics, x + 1, secondY + 1, width - 2, chartHeight, "RAM", "%",
                    samples, axisMaximum(1, samples, width - 2),
                    Theme.MEMORY_SERIES, -1, legend ? null : coverage,
                    timeLabels(TimeSeriesBuffer.Range.FIFTEEN_MINUTES), Theme.BRAILLE);
            if (legend) {
                drawLegend(graphics, x + 2, secondY + 1 + chartHeight, width - 4, "● used", coverage);
            }
        }
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
                ? "Events • " + eventLog.size()
                : "Events • " + entries.size() + "/" + eventLog.size() + " (filtered)";
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
            case 0 -> Theme.CPU_SERIES;
            case 1 -> Theme.MEMORY_SERIES;
            case 4 -> Theme.IO_SERIES;
            default -> Theme.PRIMARY;
        };
    }

    private static String[] compactValues(DashboardSnapshot snapshot) {
        String[] values = cardValues(snapshot);
        return new String[]{"CPU " + values[0], "RAM " + values[1],
            "Temp " + values[2], "GPU " + values[3], "I/O " + values[4]};
    }

    private static String[] cardValues(DashboardSnapshot snapshot) {
        CpuSnapshot cpu = snapshot == null ? null : snapshot.cpu();
        GpuSnapshot gpu = firstGpu(snapshot == null ? null : snapshot.gpu());
        Double diskIo = totalFilesystemIo(snapshot == null ? null : snapshot.disk());
        return new String[]{percent(cpu == null ? null : cpu.totalUsagePercent()),
            percent(snapshot == null || snapshot.memory() == null ? null
            : ratio(snapshot.memory().usedBytes(), snapshot.memory().totalBytes())),
            temperature(cpu == null ? null : cpu.temperatureCelsius()),
            percent(gpu == null ? null : gpu.utilizationPercent()),
            rate(diskIo)};
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

    private static String memory(Long bytes) {
        return bytes == null ? "n/a" : gib(bytes) + "GiB";
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
