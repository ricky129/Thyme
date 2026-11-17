package com.ricky.thyme.feature.dashboard;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TerminalPosition;
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
import com.ricky.thyme.ui.widgets.StatCard;
import com.ricky.thyme.ui.widgets.Sparkline;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Render-only responsive composition for the system overview. */
public final class DashboardOverviewRenderer {
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    private static final String[] DOCK_LABELS = {"Home", "Containers"};

    private DashboardOverviewRenderer() {
        // Utility class.
    }

    /** Draws the overview with the first KPI selected. */
    public static void draw(TextGraphics graphics, TerminalSize size, DashboardSnapshot snapshot,
            TimeSeriesBuffer cpuHistory, TimeSeriesBuffer memoryHistory,
            EventLogBuffer eventLog, String error) {
        draw(graphics, size, snapshot, cpuHistory, memoryHistory,
                new TimeSeriesBuffer(), new TimeSeriesBuffer(), new TimeSeriesBuffer(),
                eventLog, error, 0, false);
    }

    /** Draws the overview at one of the supported terminal breakpoints. */
    public static void draw(TextGraphics graphics, TerminalSize size, DashboardSnapshot snapshot,
            TimeSeriesBuffer cpuHistory, TimeSeriesBuffer memoryHistory,
            EventLogBuffer eventLog, String error, int selectedCard) {
        draw(graphics, size, snapshot, cpuHistory, memoryHistory,
                new TimeSeriesBuffer(), new TimeSeriesBuffer(), new TimeSeriesBuffer(),
                eventLog, error, selectedCard, false);
    }

    /** Compatibility overload for callers that only provide CPU and RAM histories. */
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

    /** Draws the overview or the selected KPI detail overlay. */
    public static void draw(TextGraphics graphics, TerminalSize size, DashboardSnapshot snapshot,
            TimeSeriesBuffer cpuHistory, TimeSeriesBuffer memoryHistory,
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
        drawFooter(graphics, width, footerY);
        if (details) {
            drawOverview(graphics, size, snapshot, cpuHistory, memoryHistory,
                    temperatureHistory, gpuHistory, diskIoHistory, eventLog, error, selectedCard);
            drawDetail(graphics, width, footerY - 2, snapshot, cpuHistory, memoryHistory,
                    temperatureHistory, gpuHistory, diskIoHistory, selectedCard,
                    TimeSeriesBuffer.Range.FIFTEEN_MINUTES, -1, false, true, List.of(), 0, new int[0]);
            return;
        }
        drawOverview(graphics, size, snapshot, cpuHistory, memoryHistory,
                temperatureHistory, gpuHistory, diskIoHistory, eventLog, error, selectedCard);
    }

    private static void drawOverview(TextGraphics graphics, TerminalSize size, DashboardSnapshot snapshot,
            TimeSeriesBuffer cpuHistory, TimeSeriesBuffer memoryHistory,
            TimeSeriesBuffer temperatureHistory, TimeSeriesBuffer gpuHistory, TimeSeriesBuffer diskIoHistory,
            EventLogBuffer eventLog, String error, int selectedCard) {
        cpuHistory = safeHistory(cpuHistory);
        memoryHistory = safeHistory(memoryHistory);
        temperatureHistory = safeHistory(temperatureHistory);
        gpuHistory = safeHistory(gpuHistory);
        diskIoHistory = safeHistory(diskIoHistory);
        eventLog = eventLog == null ? new EventLogBuffer() : eventLog;
        int width = size.getColumns();
        int height = size.getRows();
        int footerY = height - 1;
        int contentY = 2;
        int contentHeight = footerY - contentY;
        if (width < 80) {
            drawCompact(graphics, width, contentY, contentHeight, snapshot, cpuHistory, memoryHistory, error);
            return;
        }
        int cardsHeight = width >= 120 ? 5 : 10;
            drawCards(graphics, width, contentY, cardsHeight, snapshot, cpuHistory, memoryHistory,
                    temperatureHistory, gpuHistory, diskIoHistory, selectedCard);
        int bodyY = contentY + cardsHeight + 1;
        int bodyHeight = Math.max(4, footerY - bodyY);
        if (width >= 120) {
            int eventsWidth = Math.max(28, width / 3);
            drawCharts(graphics, 0, bodyY, width - eventsWidth - 1, bodyHeight,
                    snapshot, cpuHistory, memoryHistory, error);
            drawEvents(graphics, width - eventsWidth, bodyY, eventsWidth, bodyHeight, eventLog);
        } else {
            drawCharts(graphics, 0, bodyY, width, bodyHeight, snapshot, cpuHistory, memoryHistory, error);
        }
    }

    /** Draws the overview and a detail overlay using the current interaction state. */
    public static void draw(TextGraphics graphics, TerminalSize size, DashboardSnapshot snapshot,
            TimeSeriesBuffer cpuHistory, TimeSeriesBuffer memoryHistory,
            TimeSeriesBuffer temperatureHistory, TimeSeriesBuffer gpuHistory,
            TimeSeriesBuffer diskIoHistory,
            EventLogBuffer eventLog, String error, int selectedCard, boolean details,
            TimeSeriesBuffer.Range range, int cursor, boolean perCore, boolean explainer) {
        draw(graphics, size, snapshot, cpuHistory, memoryHistory, temperatureHistory, gpuHistory,
                diskIoHistory, eventLog, error, selectedCard, details, range, cursor, perCore,
                explainer, List.of(), 0, new int[0]);
    }

    /** Draws the overview and detail overlay with per-core CPU histories. */
    public static void draw(TextGraphics graphics, TerminalSize size, DashboardSnapshot snapshot,
            TimeSeriesBuffer cpuHistory, TimeSeriesBuffer memoryHistory,
            TimeSeriesBuffer temperatureHistory, TimeSeriesBuffer gpuHistory,
            TimeSeriesBuffer diskIoHistory, EventLogBuffer eventLog, String error,
            int selectedCard, boolean details, TimeSeriesBuffer.Range range, int cursor,
            boolean perCore, boolean explainer, List<TimeSeriesBuffer> coreHistories,
            int selectedCore, int[] coreCursors) {
        cpuHistory = safeHistory(cpuHistory);
        memoryHistory = safeHistory(memoryHistory);
        temperatureHistory = safeHistory(temperatureHistory);
        gpuHistory = safeHistory(gpuHistory);
        diskIoHistory = safeHistory(diskIoHistory);
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
        drawFooter(graphics, width, height - 1);
        drawOverview(graphics, size, snapshot, cpuHistory, memoryHistory,
                temperatureHistory, gpuHistory, diskIoHistory, eventLog, error, selectedCard);
        if (details) {
            drawDetail(graphics, width, height - 3, snapshot, cpuHistory, memoryHistory,
                    temperatureHistory, gpuHistory, diskIoHistory, selectedCard,
                    range, cursor, perCore, explainer, coreHistories, selectedCore, coreCursors);
        }
    }

    private static TimeSeriesBuffer safeHistory(TimeSeriesBuffer history) {
        return history == null ? new TimeSeriesBuffer() : history;
    }

    private static void drawDetail(TextGraphics graphics, int width, int height,
            DashboardSnapshot snapshot, TimeSeriesBuffer cpuHistory,
            TimeSeriesBuffer memoryHistory, TimeSeriesBuffer temperatureHistory, TimeSeriesBuffer gpuHistory,
            TimeSeriesBuffer diskIoHistory, int selectedCard,
            TimeSeriesBuffer.Range range, int cursor, boolean perCore, boolean explainer,
            List<TimeSeriesBuffer> coreHistories, int selectedCore, int[] coreCursors) {
        String[] labels = {"CPU", "RAM", "Temperature", "GPU", "Disk I/O"};
        String label = labels[Math.max(0, Math.min(labels.length - 1, selectedCard))];
        int overlayWidth = Math.max(44, Math.min(width - 4, (int) Math.round(width * 0.85)));
        int overlayHeight = Math.max(11, Math.min(height - 1, (int) Math.round(height * 0.90)));
        int x = (width - overlayWidth) / 2;
        int y = Math.max(2, (height - overlayHeight) / 2 + 1);
        graphics.setBackgroundColor(Theme.SURFACE);
        graphics.fillRectangle(new TerminalPosition(x, y), new TerminalSize(overlayWidth, overlayHeight), ' ');
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
        if (perCore && selectedCard == 0) {
            int chartY = y + 4;
            boolean compact = overlayHeight < 20;
            int gridHeight = Math.max(3, overlayHeight - (explainer ? 13 : 10));
            if (compact) gridHeight = Math.max(1, overlayHeight - 5);
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
                    "↑↓ core   ←/→ cursor   [ ] range   Tab total/per-core   Esc close", Theme.INK_MUTED);
            return;
        }
        double[] samples = history == null ? new double[0] : history.samples(range);
        int chartY = y + 4;
        int chartHeight = Math.max(4, overlayHeight - (explainer ? 13 : 10));
        double max = axisMaximum(selectedCard, samples, history, range);
        if (history == null) {
            Theme.put(graphics, innerX, chartY, innerW, "history unavailable for this metric", Theme.INK_MUTED);
        } else {
            LineChart.draw(graphics, x + 1, chartY, overlayWidth - 2, chartHeight, label,
                    chartUnit(selectedCard), samples, max, Theme.PRIMARY, cursor,
                    history.coverageNote(range), false);
            if (cursor >= 0 && cursor < samples.length && !Double.isNaN(samples[cursor])) {
                Theme.put(graphics, innerX, chartY + chartHeight, innerW,
                        "▸ cursor " + formatMetric(samples[cursor], selectedCard), Theme.ACCENT);
            }
        }
        int detailY = chartY + chartHeight + 1;
        if (explainer && detailY < y + overlayHeight - 2) {
            Panel.draw(graphics, innerX, detailY, innerW, 3, "What is " + label + "?");
            Theme.put(graphics, innerX + 1, detailY + 1, innerW - 2, explanation(selectedCard), Theme.INK_SECONDARY);
            detailY += 4;
        }
        drawDetailSummary(graphics, snapshot, selectedCard, innerX, detailY, innerW, perCore, selectedCore);
        Theme.put(graphics, innerX, y + overlayHeight - 1, innerW,
                "←/→ cursor   [ ] range   e explain   Esc close", Theme.INK_MUTED);
    }

    private static void drawDetailSummary(TextGraphics graphics, DashboardSnapshot snapshot,
            int card, int x, int y, int width, boolean perCore, int selectedCore) {
        String text = switch (card) {
                case 0 -> snapshot == null || snapshot.cpu() == null ? "CPU data unavailable"
                        : (perCore ? "Per-core selector active" : "Total CPU selected")
                                + "  cores " + snapshot.cpu().logicalProcessors()
                                + (perCore ? "  active core " + selectedCore : "");
                case 1 -> snapshot == null || snapshot.memory() == null ? "Memory data unavailable"
                        : "used " + gib(snapshot.memory().usedBytes()) + " / " + gib(snapshot.memory().totalBytes()) + " GiB"
                                + "  swap " + gib(snapshot.memory().swapUsedBytes()) + " / " + gib(snapshot.memory().swapTotalBytes());
                case 2 -> snapshot == null || snapshot.cpu() == null ? "Temperature data unavailable"
                        : "temperature " + temperature(snapshot.cpu().temperatureCelsius());
                case 3 -> snapshot == null || snapshot.gpu() == null ? "GPU data unavailable"
                        : "devices " + snapshot.gpu().devices().size();
                case 4 -> snapshot == null || snapshot.disk() == null ? "Disk data unavailable"
                        : "devices " + snapshot.disk().devices().size() + "  filesystems " + snapshot.disk().filesystems().size();
                default -> "Metric data unavailable";
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
                        histories.get(index).samples(range), Theme.PRIMARY, false);
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
                        samples, 100, Theme.PRIMARY, cursor,
                        null, false);
            } else {
                Panel.draw(graphics, cellX, cellY, actualWidth, actualHeight, title);
                Sparkline.draw(graphics, cellX + 1, cellY + 1, actualWidth - 2,
                        samples, Theme.PRIMARY, false);
            }
        }
    }

    private static String rangeTabs(TimeSeriesBuffer.Range range) {
        return switch (range) {
            case FIFTEEN_MINUTES -> "[15M]  1H  6H";
            case ONE_HOUR -> " 15M  [1H]  6H";
            case SIX_HOURS -> " 15M   1H  [6H]";
        };
    }

    private static String current(TimeSeriesBuffer history, TimeSeriesBuffer.Range range, int card) {
        if (history == null || Double.isNaN(history.current(range))) return "n/a";
        return formatMetric(history.current(range), card);
    }

    private static String formatMetric(double value, int card) {
        if (card == 2) return String.format("%.1f°C", value);
        if (card == 4) return String.format("%.1fM/s", value);
        return String.format("%.1f%%", value);
    }

    private static double axisMaximum(int card, double[] samples, TimeSeriesBuffer history,
            TimeSeriesBuffer.Range range) {
        if (card == 0 || card == 1 || card == 3) return 100;
        double peak = history == null ? Double.NaN : history.peak(range);
        if (Double.isNaN(peak)) {
            peak = 1;
            for (double sample : samples) if (!Double.isNaN(sample)) peak = Math.max(peak, sample);
        }
        double target = Math.max(1, peak * 1.1);
        double scale = Math.pow(10, Math.floor(Math.log10(target)));
        for (double step : new double[] {1, 2, 5, 10}) if (step * scale >= target) return step * scale;
        return 10 * scale;
    }

    private static String explanation(int card) {
        return switch (card) {
            case 0 -> "Share of time processors were busy, averaged over all cores.";
            case 1 -> "Memory currently occupied by applications and the operating system.";
            case 2 -> "The latest reported thermal sensor value.";
            case 3 -> "Work currently scheduled on the graphics device.";
            case 4 -> "Combined read and write throughput from sampled devices.";
            default -> "Metric explanation unavailable.";
        };
    }

    private static TimeSeriesBuffer historyForCard(int selectedCard,
            TimeSeriesBuffer cpuHistory, TimeSeriesBuffer memoryHistory,
            TimeSeriesBuffer temperatureHistory, TimeSeriesBuffer gpuHistory,
            TimeSeriesBuffer diskIoHistory) {
        return switch (selectedCard) {
            case 0 -> cpuHistory;
            case 1 -> memoryHistory;
            case 2 -> temperatureHistory;
            case 3 -> gpuHistory;
            case 4 -> diskIoHistory;
            default -> null;
        };
    }

    private static String aggregate(TimeSeriesBuffer history, int selectedCard,
            TimeSeriesBuffer.Range range, boolean peak) {
        if (history == null) return "n/a";
        double value = peak ? history.peak(range) : history.average(range);
        if (Double.isNaN(value)) return "n/a";
        return formatMetric(value, selectedCard);
    }

    private static void drawHeader(TextGraphics graphics, int width, DashboardSnapshot snapshot) {
        String host = System.getenv().getOrDefault("HOSTNAME", "host");
        String status = snapshot == null ? "○" : "●";
        TextColor statusColor = snapshot == null ? Theme.WARM : healthColor(snapshot);
        Theme.put(graphics, 1, 0, width - 2, "THYME  " + Theme.clipToWidth(host, 20), Theme.INK, true);
        Theme.put(graphics, Math.max(1, width - 35), 0, 34,
                status + "  up  LIVE • " + (snapshot == null ? "--:--:--" : CLOCK.format(snapshot.sampledAt())),
                statusColor);
        graphics.setForegroundColor(Theme.HAIRLINE);
        graphics.drawLine(0, 1, width - 1, 1, Theme.H);
    }

    private static void drawFooter(TextGraphics graphics, int width, int y) {
        graphics.setForegroundColor(Theme.HAIRLINE);
        graphics.drawLine(0, y, width - 1, y, Theme.H);
        Dock.draw(graphics, 1, y, width - 2, DOCK_LABELS, 0);
        Theme.put(graphics, Math.max(1, width - 29), y, 28, "↑↓ select  ? help  q quit", Theme.INK_MUTED);
    }

    private static void drawCompact(TextGraphics graphics, int width, int y, int height,
            DashboardSnapshot snapshot, TimeSeriesBuffer cpuHistory,
            TimeSeriesBuffer memoryHistory, String error) {
        String[] values = compactValues(snapshot);
        Theme.put(graphics, 1, y, width - 2, String.join("  ", values), Theme.INK_SECONDARY);
        int chartY = y + 2;
        int chartHeight = Math.max(4, height - 2);
        Panel.draw(graphics, 0, chartY, width, chartHeight, "CPU / RAM • 15M");
        if (error != null) {
            Theme.put(graphics, 2, chartY + 2, width - 4, error, Theme.CRITICAL);
        } else {
            LineChart.draw(graphics, 1, chartY + 1, width - 2, chartHeight - 2,
                    "CPU / RAM", "%", cpuHistory.samples(TimeSeriesBuffer.Range.FIFTEEN_MINUTES),
                    100, Theme.PRIMARY, -1, cpuHistory.coverageNote(TimeSeriesBuffer.Range.FIFTEEN_MINUTES), false);
        }
    }

    private static void drawCards(TextGraphics graphics, int width, int y, int height,
            DashboardSnapshot snapshot, TimeSeriesBuffer cpuHistory, TimeSeriesBuffer memoryHistory,
            TimeSeriesBuffer temperatureHistory, TimeSeriesBuffer gpuHistory,
            TimeSeriesBuffer diskIoHistory, int selectedCard) {
        int columns = width >= 120 ? 5 : 3;
        int gap = 1;
        int cardWidth = (width - (columns - 1) * gap) / columns;
        String[] labels = {"CPU", "RAM", "Temp", "GPU", "Disk I/O"};
        String[] values = cardValues(snapshot);
        double[][] history = {cpuHistory.samples(TimeSeriesBuffer.Range.FIFTEEN_MINUTES),
                memoryHistory.samples(TimeSeriesBuffer.Range.FIFTEEN_MINUTES),
                temperatureHistory.samples(TimeSeriesBuffer.Range.FIFTEEN_MINUTES),
                gpuHistory.samples(TimeSeriesBuffer.Range.FIFTEEN_MINUTES),
                diskIoHistory.samples(TimeSeriesBuffer.Range.FIFTEEN_MINUTES)};
        for (int i = 0; i < labels.length; i++) {
            int row = width >= 120 ? 0 : i / columns;
            int column = i % columns;
            int x = column * (cardWidth + gap);
            int cardY = y + row * (height / 2 + 1);
            StatCard.draw(graphics, x, cardY, cardWidth, width >= 120 ? height : height / 2,
                    labels[i], values[i], cardColor(labels[i], snapshot), history[i],
                    i < 3 ? "current" : "latest sample", true, i == selectedCard);
        }
    }

    private static void drawCharts(TextGraphics graphics, int x, int y, int width, int height,
            DashboardSnapshot snapshot, TimeSeriesBuffer cpuHistory,
            TimeSeriesBuffer memoryHistory, String error) {
        if (width < 20 || height < 4) return;
        int firstHeight = Math.max(4, height / 2);
        Panel.draw(graphics, x, y, width, firstHeight, "CPU • 15M");
        if (error != null) {
            Theme.put(graphics, x + 2, y + 2, width - 4, error, Theme.CRITICAL);
        } else {
            LineChart.draw(graphics, x + 1, y + 1, width - 2, firstHeight - 2, "CPU", "%",
                    cpuHistory.samples(TimeSeriesBuffer.Range.FIFTEEN_MINUTES), 100,
                    Theme.PRIMARY, -1, cpuHistory.coverageNote(TimeSeriesBuffer.Range.FIFTEEN_MINUTES), false);
        }
        int secondY = y + firstHeight + 1;
        int secondHeight = height - firstHeight - 1;
        if (secondHeight >= 4) {
            Panel.draw(graphics, x, secondY, width, secondHeight, "RAM • 15M");
            LineChart.draw(graphics, x + 1, secondY + 1, width - 2, secondHeight - 2, "RAM", "%",
                    memoryHistory.samples(TimeSeriesBuffer.Range.FIFTEEN_MINUTES), 100,
                    Theme.SECONDARY, -1, memoryHistory.coverageNote(TimeSeriesBuffer.Range.FIFTEEN_MINUTES), false);
        }
    }

    private static void drawEvents(TextGraphics graphics, int x, int y, int width, int height,
            EventLogBuffer eventLog) {
        List<EventLog.Entry> entries = new ArrayList<>();
        for (EventLogBuffer.Event event : eventLog.snapshot()) {
            entries.add(new EventLog.Entry(CLOCK.format(event.time()), severityColor(event.severity()), event.message()));
        }
        EventLog.draw(graphics, x, y, width, height, "Events • " + eventLog.size(), entries);
    }

    private static String chartUnit(int selectedCard) {
        return switch (selectedCard) {
            case 2 -> "°C";
            case 4 -> "M/s";
            default -> "%";
        };
    }

    private static void drawMetricDetails(TextGraphics graphics, DashboardSnapshot snapshot,
            int selectedCard, int width, int height) {
        int row = 19;
        if (snapshot == null || height < 25) return;
        switch (selectedCard) {
            case 0 -> drawCpuDetails(graphics, snapshot.cpu(), width, row);
            case 1 -> drawMemoryDetails(graphics, snapshot.memory(), width, row);
            case 2 -> drawTemperatureDetails(graphics, snapshot.cpu(), width, row);
            case 3 -> drawGpuDetails(graphics, snapshot.gpu(), width, row);
            case 4 -> drawDiskDetails(graphics, snapshot.disk(), width, row);
            default -> { }
        }
    }

    private static void drawCpuDetails(TextGraphics graphics, CpuSnapshot cpu, int width, int row) {
        if (cpu == null) {
            Theme.put(graphics, 2, row, width - 4, "CPU data unavailable", Theme.INK_MUTED);
            return;
        }
        Theme.put(graphics, 2, row++, width - 4, "CPU " + cpu.model() + "  "
                + percent(cpu.totalUsagePercent()) + "  " + temperature(cpu.temperatureCelsius()), Theme.INK_SECONDARY);
        for (CpuSnapshot.Core core : cpu.cores()) {
            if (row >= 25) break;
            Theme.put(graphics, 4, row++, width - 6, "core " + core.index() + "  "
                    + percent(core.usagePercent()) + "  " + frequency(core.frequencyHz()), Theme.INK_MUTED);
        }
    }

    private static void drawMemoryDetails(TextGraphics graphics, com.ricky.thyme.feature.memory.MemorySnapshot memory,
            int width, int row) {
        if (memory == null) {
            Theme.put(graphics, 2, row, width - 4, "RAM data unavailable", Theme.INK_MUTED);
            return;
        }
        Theme.put(graphics, 2, row++, width - 4, "RAM used " + gib(memory.usedBytes()) + " / "
                + gib(memory.totalBytes()) + " GiB  (" + percent(ratio(memory.usedBytes(), memory.totalBytes())) + ")",
                Theme.INK_SECONDARY);
        Theme.put(graphics, 2, row, width - 4, "Swap used " + gib(memory.swapUsedBytes()) + " / "
                + gib(memory.swapTotalBytes()) + " GiB  (" + percent(ratio(memory.swapUsedBytes(), memory.swapTotalBytes())) + ")",
                Theme.INK_MUTED);
    }

    private static void drawTemperatureDetails(TextGraphics graphics, CpuSnapshot cpu, int width, int row) {
        Theme.put(graphics, 2, row, width - 4, cpu == null ? "Temperature data unavailable"
                : "CPU temperature " + temperature(cpu.temperatureCelsius()), Theme.temperatureColor(
                        cpu == null ? null : cpu.temperatureCelsius()));
    }

    private static void drawGpuDetails(TextGraphics graphics, GpuReading gpu, int width, int row) {
        if (gpu == null || gpu.devices().isEmpty()) {
            Theme.put(graphics, 2, row, width - 4, "GPU data unavailable", Theme.INK_MUTED);
            return;
        }
        for (GpuSnapshot device : gpu.devices()) {
            if (row >= 25) break;
            Theme.put(graphics, 2, row++, width - 4, device.vendor() + "  " + device.name()
                    + "  usage " + percent(device.utilizationPercent()) + "  temp " + temperature(device.temperatureCelsius()),
                    Theme.utilizationColor(device.utilizationPercent()));
            if (row < 25) Theme.put(graphics, 4, row++, width - 6, "VRAM " + memory(device.memoryUsedBytes())
                    + " / " + memory(device.memoryTotalBytes()) + "  core " + mhz(device.graphicsClockMhz())
                    + "  memory " + mhz(device.memoryClockMhz()) + "  power " + watts(device.powerWatts())
                    + " / " + watts(device.powerLimitWatts()), Theme.INK_MUTED);
        }
    }

    private static void drawDiskDetails(TextGraphics graphics, DiskSnapshot disk, int width, int row) {
        if (disk == null) {
            Theme.put(graphics, 2, row, width - 4, "Disk data unavailable", Theme.INK_MUTED);
            return;
        }
        for (DiskSnapshot.Device device : disk.devices()) {
            if (row >= 25) break;
            Theme.put(graphics, 2, row++, width - 4, device.name() + "  read " + rate(device.readBytesPerSecond())
                    + "  write " + rate(device.writeBytesPerSecond()) + "  util " + percent(device.utilizationPercent()),
                    Theme.INK_SECONDARY);
        }
        for (DiskSnapshot.Filesystem filesystem : disk.filesystems()) {
            if (row >= 25) break;
            Theme.put(graphics, 2, row++, width - 4, filesystem.mountPoint() + "  "
                    + gib(filesystem.usedBytes()) + " / " + gib(filesystem.totalBytes()) + " GiB  "
                    + percent(filesystem.utilizationPercent()), Theme.filesystemColor(filesystem.utilizationPercent()));
        }
    }

    private static String[] compactValues(DashboardSnapshot snapshot) {
        String[] values = cardValues(snapshot);
        return new String[] {"CPU " + values[0], "RAM " + values[1],
                "Temp " + values[2], "GPU " + values[3], "I/O " + values[4]};
    }

    private static String[] cardValues(DashboardSnapshot snapshot) {
        CpuSnapshot cpu = snapshot == null ? null : snapshot.cpu();
        GpuSnapshot gpu = firstGpu(snapshot == null ? null : snapshot.gpu());
        Double diskIo = firstDiskIo(snapshot);
        return new String[] {percent(cpu == null ? null : cpu.totalUsagePercent()),
                percent(snapshot == null || snapshot.memory() == null ? null
                        : ratio(snapshot.memory().usedBytes(), snapshot.memory().totalBytes())),
                temperature(cpu == null ? null : cpu.temperatureCelsius()),
                percent(gpu == null ? null : gpu.utilizationPercent()),
                rate(diskIo)};
    }

    private static TextColor cardColor(String label, DashboardSnapshot snapshot) {
        if ("Temp".equals(label)) {
            CpuSnapshot cpu = snapshot == null ? null : snapshot.cpu();
            return Theme.temperatureColor(cpu == null ? null : cpu.temperatureCelsius());
        }
        if ("RAM".equals(label)) {
            return Theme.utilizationColor(snapshot == null || snapshot.memory() == null ? null
                    : ratio(snapshot.memory().usedBytes(), snapshot.memory().totalBytes()));
        }
        if ("GPU".equals(label)) {
            GpuSnapshot gpu = firstGpu(snapshot == null ? null : snapshot.gpu());
            return Theme.utilizationColor(gpu == null ? null : gpu.utilizationPercent());
        }
        CpuSnapshot cpu = snapshot == null ? null : snapshot.cpu();
        return "CPU".equals(label) ? Theme.utilizationColor(cpu == null ? null : cpu.totalUsagePercent()) : Theme.INK;
    }

    private static TextColor healthColor(DashboardSnapshot snapshot) {
        if (!snapshot.errors().isEmpty()) return Theme.CRITICAL;
        CpuSnapshot cpu = snapshot.cpu();
        return Theme.utilizationColor(cpu == null ? null : cpu.totalUsagePercent());
    }

    private static GpuSnapshot firstGpu(GpuReading gpu) {
        return gpu == null || gpu.devices().isEmpty() ? null : gpu.devices().get(0);
    }

    private static Double firstDiskIo(DashboardSnapshot snapshot) {
        if (snapshot == null || snapshot.disk() == null || snapshot.disk().devices().isEmpty()) return null;
        var disk = snapshot.disk().devices().get(0);
        if (disk.readBytesPerSecond() == null && disk.writeBytesPerSecond() == null) return null;
        return (disk.readBytesPerSecond() == null ? 0 : disk.readBytesPerSecond())
                + (disk.writeBytesPerSecond() == null ? 0 : disk.writeBytesPerSecond());
    }

    private static String percent(Double value) { return value == null ? "n/a" : String.format("%.0f%%", value); }
    private static String decimal(Double value) { return value == null ? "n/a" : String.format("%.2f", value); }
    private static String temperature(Double value) { return value == null ? "n/a" : String.format("%.0f°C", value); }
    private static String frequency(Long value) { return value == null ? "n/a" : String.format("%.2fGHz", value / 1_000_000_000.0); }
    private static String gib(long bytes) { return String.format("%.1f", bytes / (1024.0 * 1024 * 1024)); }
    private static String memory(Long bytes) { return bytes == null ? "n/a" : gib(bytes) + "GiB"; }
    private static String mhz(Long value) { return value == null ? "n/a" : value + "MHz"; }
    private static String watts(Double value) { return value == null ? "n/a" : String.format("%.0fW", value); }
    private static String rate(Double value) {
        return value == null ? "n/a" : String.format("%.1fM/s", value / (1024 * 1024));
    }
    private static double ratio(long used, long total) { return total == 0 ? 0 : used * 100.0 / total; }
    private static TextColor severityColor(EventLogBuffer.Severity severity) {
        return switch (severity) {
            case GOOD -> Theme.GOOD;
            case WARM -> Theme.WARM;
            case CRITICAL -> Theme.CRITICAL;
        };
    }
}
