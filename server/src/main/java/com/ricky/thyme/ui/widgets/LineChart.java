package com.ricky.thyme.ui.widgets;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.ricky.thyme.ui.Theme;
import java.util.List;

/**
 * A line chart widget drawn with Unicode block elements (or braille when
 * available).
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
        drawCrosshair(g, plotX, plotY, plotW, plotH, series.get(0).samples(), cursorIndex);
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
        if (value >= 1000) return (int)(value / 1000) + "k" + (unit != null ? unit : "");
        return (int) value + (unit != null ? unit : "");
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

        // Sample window: most-recent 'w' samples (or '2w' in braille mode)
        int capacity = braille ? w * 2 : w;
        int start = Math.max(0, samples.length - capacity);

        g.setForegroundColor(color);

        for (int col = 0; col < w; col++) {
            if (braille) {
                // Each column represents two consecutive samples
                int li = start + col * 2 - Math.max(0, capacity - (samples.length - start));
                int ri = li + 1;
                double lv = (li >= 0 && li < samples.length) ? samples[li] : 0;
                double rv = (ri >= 0 && ri < samples.length) ? samples[ri] : 0;
                drawBrailleColumn(g, x + col, y, h, lv, rv, maxValue, color);
            } else {
                int si = start + col;
                if (si >= samples.length) break;
                if (!Double.isNaN(samples[si])) {
                    double v = Math.max(0, Math.min(maxValue, samples[si]));
                    int screenCol = samples.length < w ? w - samples.length + si : col;
                    drawBlockColumn(g, x + screenCol, y, h, v, maxValue, color);
                }
            }
        }

        // Crosshair
        if (cursorIndex >= 0 && samples.length > 0) {
            int clampedCursor = Math.max(0, Math.min(samples.length - 1, cursorIndex));
            // Map logical cursor to screen column
            drawCrosshair(g, x, y, w, h, samples, clampedCursor);
        }
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

    private static void drawCrosshair(TextGraphics g, int x, int y, int w, int h, double[] samples, int cursor) {
        if (samples == null || samples.length == 0 || cursor < 0) return;
        int clamped = Math.max(0, Math.min(samples.length - 1, cursor));
        int screenCol = w - (samples.length - clamped);
        if (screenCol < 0 || screenCol >= w) return;
        g.setForegroundColor(Theme.ACCENT);
        for (int row = y; row < y + h; row++) g.putString(x + screenCol, row, "│");
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

    private static void drawBrailleColumn(
            TextGraphics g, int x, int y, int h,
            double leftVal, double rightVal, double maxValue, TextColor color) {
        if (maxValue <= 0) return;
        g.setForegroundColor(color);
        // For each row, determine filled dots using braille encoding
        for (int row = 0; row < h; row++) {
            int plotRow = y + h - 1 - row;
            // Row covers value range [(row)/h, (row+1)/h] of maxValue
            double rowLo = (double) row / h * maxValue;
            boolean lFill = !Double.isNaN(leftVal) && leftVal >= rowLo;
            boolean rFill = !Double.isNaN(rightVal) && rightVal >= rowLo;
            if (!lFill && !rFill) continue;

            // Encode as a simple braille cell: left dot, right dot at top of this row
            // Using dots 7 (bit 0x40) for left and 8 (bit 0x80) for right for bottom-row feel,
            // or a full block when both sides and high fill
            if (lFill && rFill) {
                g.putString(x, plotRow, "█");
            } else if (lFill) {
                // Left half block
                g.putString(x, plotRow, "▌");
            } else {
                // Right half block
                g.putString(x, plotRow, "▐");
            }
        }
    }
}
