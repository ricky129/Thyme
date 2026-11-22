package com.ricky.thyme.ui.widgets;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.ricky.thyme.ui.Theme;
import java.util.List;
import java.util.Locale;

/**
 * An area chart widget drawn as a btop-style braille dot matrix, with a
 * solid block fallback for terminals without braille.
 *
 * <p>Features:
 * <ul>
 *   <li>Labelled Y-axis on the left.</li>
 *   <li>Labelled X-axis (timestamps) on the bottom.</li>
 *   <li>Movable crosshair: a vertical bar at a logical cursor position with
 *       the sample value rendered below the chart.</li>
 *   <li>Coverage note ("history covers Xm of 15M") when fewer samples than
 *       the selected interval are available.</li>
 * </ul>
 *
 * <p>This widget is <em>render-only</em> and holds no domain state.
 */
public final class LineChart {

    /** A render-only series for a multi-line chart. Dashed series draw a dotted line. */
    public record Series(String label, double[] samples, TextColor color, boolean dashed) { }

    private static final int Y_AXIS_WIDTH = 5; // "100% " or " n/a "
    private static final int X_AXIS_HEIGHT = 1;

    /** Braille bits for the left and right column of a 2x4 cell, bottom-up. */
    private static final int[] LEFT_DOTS = {0x40, 0x04, 0x02, 0x01};
    private static final int[] RIGHT_DOTS = {0x80, 0x20, 0x10, 0x08};

    private LineChart() {}

    /**
     * Draws the chart inside the given rectangle.
     *
     * @param g            graphics context
     * @param x            left column of the whole chart area (including axes)
     * @param y            top row of the chart area
     * @param w            total width  (must be ≥ 8)
     * @param h            total height (must be ≥ 4)
     * @param title        chart title placed in the top-left
     * @param unit         unit string for Y-axis labels (e.g. {@code "%"} or {@code "M/s"})
     * @param samples      data samples in [0, 100]; most recent last
     * @param maxValue     maximum value that maps to 100 % on Y (use 100 for %-metrics)
     * @param color        line/fill colour
     * @param cursorIndex  logical cursor position (0 = oldest visible, samples.length-1 = newest);
     *                     negative to hide the crosshair
     * @param coverageNote optional note drawn below the title (may be {@code null})
     * @param braille      whether to use braille glyphs for higher resolution
     */
    public static void draw(
            TextGraphics g,
            int x, int y, int w, int h,
            String title,
            String unit,
            double[] samples,
            double maxValue,
            TextColor color,
            int cursorIndex,
            String coverageNote,
            boolean braille) {

        if (w < 8 || h < 4) return;

        // ── Layout ──────────────────────────────────────────────────────────
        int plotX = x + Y_AXIS_WIDTH;
        int plotY = y + 1; // row 0 = title
        int plotW = w - Y_AXIS_WIDTH;
        int plotH = h - 1 - X_AXIS_HEIGHT; // minus title row and X-axis row
        if (plotW < 2 || plotH < 1) return;

        // ── Title row ───────────────────────────────────────────────────────
        Theme.put(g, x, y, w, title != null ? title : "", Theme.INK, true);
        if (coverageNote != null && w > (title != null ? title.length() : 0) + 2) {
            int noteX = x + (title != null ? title.length() : 0) + 2;
            Theme.put(g, noteX, y, w - (noteX - x), coverageNote, Theme.INK_MUTED);
        }

        // ── Y-axis ──────────────────────────────────────────────────────────
        drawYAxis(g, x, plotY, plotH, maxValue, unit);

        // ── Plot area ───────────────────────────────────────────────────────
        drawPlot(g, plotX, plotY, plotW, plotH, samples, maxValue, color, cursorIndex, braille);

        // ── X-axis (timestamps placeholder) ─────────────────────────────────
        int xAxisY = plotY + plotH;
        g.setForegroundColor(Theme.BASELINE);
        // Draw a simple horizontal axis line
        for (int col = plotX; col < plotX + plotW; col++) {
            g.putString(col, xAxisY, "─");
        }
    }

    /** Draws multiple series sharing one axis and one crosshair. */
    public static void drawMulti(TextGraphics g, int x, int y, int w, int h,
            String title, String unit, List<Series> series, double maxValue,
            int cursorIndex, String coverageNote, boolean braille) {
        if (w < 8 || h < 4 || series == null || series.isEmpty()) return;
        int plotX = x + Y_AXIS_WIDTH;
        int plotY = y + 1;
        int plotW = w - Y_AXIS_WIDTH;
        int plotH = h - 1 - X_AXIS_HEIGHT;
        if (plotW < 2 || plotH < 1) return;
        StringBuilder heading = new StringBuilder(title == null ? "" : title);
        for (Series value : series) {
            heading.append("   ").append(value.dashed() ? "○ " : "● ").append(value.label());
        }
        Theme.put(g, x, y, w, heading.toString(), Theme.INK, true);
        if (coverageNote != null) {
            int noteX = Math.max(x + heading.length() + 1, x + w - coverageNote.length());
            Theme.put(g, noteX, y, Math.max(0, x + w - noteX), coverageNote, Theme.INK_MUTED);
        }
        drawYAxis(g, x, plotY, plotH, maxValue, unit);
        for (Series value : series) {
            if (value.dashed()) {
                drawDashedLine(g, plotX, plotY, plotW, plotH, value.samples(), maxValue, value.color());
            } else {
                drawPlot(g, plotX, plotY, plotW, plotH, value.samples(), maxValue, value.color(), -1, braille);
            }
        }
        int column = cursorColumn(series.get(0).samples(), cursorIndex, plotW, braille);
        if (column >= 0) {
            drawCrosshair(g, plotX + column, plotY, plotH);
        }
        int xAxisY = plotY + plotH;
        g.setForegroundColor(Theme.BASELINE);
        for (int col = plotX; col < plotX + plotW; col++) g.putString(col, xAxisY, "─");
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private static void drawYAxis(
            TextGraphics g, int x, int y, int plotH, double maxValue, String unit) {
        // Top label
        String top = formatAxisLabel(maxValue, unit);
        Theme.put(g, x, y, Y_AXIS_WIDTH, top, Theme.INK_MUTED);
        // Middle label
        if (plotH > 2) {
            String mid = formatAxisLabel(maxValue / 2, unit);
            Theme.put(g, x, y + plotH / 2, Y_AXIS_WIDTH, mid, Theme.INK_MUTED);
        }
        // Bottom label (0)
        Theme.put(g, x, y + plotH - 1, Y_AXIS_WIDTH, "0" + unit, Theme.INK_MUTED);
    }

    private static String formatAxisLabel(double value, String unit) {
        String suffix = unit != null ? unit : "";
        if (value >= 1000) return String.format(Locale.ROOT, "%.1fk%s", value / 1000, suffix);
        if (value >= 10 || value == Math.rint(value)) {
            return Math.round(value) + suffix;
        }
        if (value >= 1) return String.format(Locale.ROOT, "%.1f%s", value, suffix);
        // Sub-unit maxima with a long unit (e.g. "M/s") would overflow the axis column.
        if (suffix.length() >= 3) return "<1" + suffix;
        return String.format(Locale.ROOT, "%.2f%s", value, suffix);
    }

    /**
     * Number of samples a chart of the given total width can display.
     * Braille cells hold two samples per terminal column.
     */
    public static int sampleCapacity(int chartWidth, boolean braille) {
        int plotWidth = Math.max(1, chartWidth - Y_AXIS_WIDTH);
        return Math.max(1, braille ? plotWidth * 2 : plotWidth);
    }

    private static void drawPlot(
            TextGraphics g,
            int x, int y, int w, int h,
            double[] samples,
            double maxValue,
            TextColor color,
            int cursorIndex,
            boolean braille) {

        if (samples == null || samples.length == 0) {
            Theme.put(g, x, y + h / 2, w, "waiting for samples", Theme.INK_MUTED);
            return;
        }

        g.setForegroundColor(color);
        if (braille) {
            drawBraillePlot(g, x, y, w, h, samples, maxValue);
        } else {
            drawBlockPlot(g, x, y, w, h, samples, maxValue, color);
        }

        if (cursorIndex >= 0) {
            int column = cursorColumn(samples, cursorIndex, w, braille);
            if (column >= 0) {
                drawCrosshair(g, x + column, y, h);
            }
        }
    }

    /** Solid block fill used when the terminal cannot render braille. */
    private static void drawBlockPlot(TextGraphics g, int x, int y, int w, int h,
            double[] samples, double maxValue, TextColor color) {
        int start = Math.max(0, samples.length - w);
        for (int col = 0; col < w; col++) {
            int si = start + col;
            if (si >= samples.length) break;
            if (!Double.isNaN(samples[si])) {
                double v = Math.max(0, Math.min(maxValue, samples[si]));
                int screenCol = samples.length < w ? w - samples.length + si : col;
                drawBlockColumn(g, x + screenCol, y, h, v, maxValue, color);
            }
        }
    }

    /**
     * Draws a btop-style dot matrix: every terminal cell is a 2x4 braille
     * cell with dots filled from the bottom up to the sample value.  Two
     * samples share one terminal column, so the horizontal resolution is
     * doubled.
     */
    private static void drawBraillePlot(TextGraphics g, int x, int y, int w, int h,
            double[] samples, double maxValue) {
        int capacity = w * 2;
        int count = Math.min(samples.length, capacity);
        int pad = capacity - count;
        char[] rowChars = new char[w];
        for (int row = 0; row < h; row++) {
            for (int col = 0; col < w; col++) {
                double left = sampleAt(samples, col * 2, pad, count);
                double right = sampleAt(samples, col * 2 + 1, pad, count);
                int leftDots = brailleDots(left, row, h, maxValue);
                int rightDots = brailleDots(right, row, h, maxValue);
                rowChars[col] = leftDots == 0 && rightDots == 0
                        ? ' ' : brailleChar(leftDots, rightDots);
            }
            g.putString(x, y + row, new String(rowChars));
        }
    }

    /** Right-aligned sample lookup over the braille half-columns. */
    private static double sampleAt(double[] samples, int position, int pad, int count) {
        if (position < pad) return Double.NaN;
        int index = samples.length - count + (position - pad);
        return index >= 0 && index < samples.length ? samples[index] : Double.NaN;
    }

    /** Number of lit dots (0-4) in one braille cell for the given value and row. */
    private static int brailleDots(double value, int row, int height, double maxValue) {
        if (Double.isNaN(value) || maxValue <= 0) return 0;
        double ratio = Math.max(0, Math.min(1.0, value / maxValue));
        int total = Math.max(1, (int) Math.round(ratio * height * 4));
        int fromBottom = (height - 1 - row) * 4;
        return Math.max(0, Math.min(4, total - fromBottom));
    }

    private static char brailleChar(int leftDots, int rightDots) {
        int bits = 0;
        for (int i = 0; i < leftDots; i++) bits |= LEFT_DOTS[i];
        for (int i = 0; i < rightDots; i++) bits |= RIGHT_DOTS[i];
        return (char) (0x2800 + bits);
    }

    /** Maps a logical cursor sample to a terminal column, or -1 when hidden/off-screen. */
    private static int cursorColumn(double[] samples, int cursor, int w, boolean braille) {
        if (cursor < 0 || samples == null || samples.length == 0) return -1;
        int capacity = braille ? w * 2 : w;
        int count = Math.min(samples.length, capacity);
        int pad = capacity - count;
        int clamped = Math.max(0, Math.min(samples.length - 1, cursor));
        int position = pad + (clamped - (samples.length - count));
        int column = braille ? position / 2 : position;
        return column >= 0 && column < w ? column : -1;
    }

    private static void drawDashedLine(TextGraphics g, int x, int y, int w, int h,
            double[] samples, double maxValue, TextColor color) {
        if (samples == null || samples.length == 0 || maxValue <= 0) return;
        int start = Math.max(0, samples.length - w);
        g.setForegroundColor(color);
        for (int col = 0; col < w; col++) {
            int si = start + col;
            if (si >= samples.length) break;
            double value = samples[si];
            if (Double.isNaN(value)) continue;
            boolean positive = value > 0;
            boolean touchesPositive = hasPositiveSample(samples, si - 1)
                    || hasPositiveSample(samples, si + 1);
            if (!positive && !touchesPositive) continue;
            int filledRows = Math.max(1, (int) Math.round(value / maxValue * h));
            int screenCol = samples.length < w ? w - samples.length + si : col;
            g.putString(x + screenCol, y + h - filledRows, "·");
        }
    }

    /** Whether the sample at {@code index} is present and above zero. */
    private static boolean hasPositiveSample(double[] samples, int index) {
        return index >= 0 && index < samples.length && samples[index] > 0;
    }

    private static void drawCrosshair(TextGraphics g, int x, int y, int h) {
        g.setForegroundColor(Theme.ACCENT);
        for (int row = y; row < y + h; row++) g.putString(x, row, "│");
    }

    private static void drawBlockColumn(
            TextGraphics g, int x, int y, int h, double value, double maxValue, TextColor color) {
        if (maxValue <= 0) return;
        double ratio = Math.max(0, Math.min(1.0, value / maxValue));
        int filledRows = (int) Math.round(ratio * h);
        g.setForegroundColor(color);
        for (int row = 0; row < h; row++) {
            int plotRow = y + h - 1 - row; // bottom-up
            if (row < filledRows) {
                g.putString(x, plotRow, "█");
            }
        }
    }

}
