package com.ricky.thyme.ui.widgets;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.ricky.thyme.ui.Theme;

/**
 * A horizontal bar chart widget — renders a series of named bars,
 * each filled proportionally to its value relative to the maximum.
 *
 * <p>Example (3 bars):
 * <pre>
 *   sda   ████████████░░░░░  12.4M/s
 *   sdb   ████░░░░░░░░░░░░░   4.1M/s
 *   nvme0 ░░░░░░░░░░░░░░░░░   0.0M/s
 * </pre>
 *
 * <p>This widget is <em>render-only</em> and holds no domain state.
 */
public final class BarChart {

    private static final String FULL  = "█";
    private static final String EMPTY = "░";

    private BarChart() {}

    /**
     * Draws the bar chart inside the given rectangle.
     *
     * @param g        graphics context
     * @param x        left column
     * @param y        top row
     * @param w        width in columns (must be ≥ 10)
     * @param h        height in rows — only the first {@code Math.min(h, labels.length)} rows are used
     * @param labels   row labels (e.g. device names)
     * @param values   current value for each row (same order as {@code labels})
     * @param max      value that maps to a full bar (must be {@code > 0})
     * @param formatted pre-formatted value strings (same order; may be {@code null} entries)
     * @param color    bar fill colour
     */
    public static void draw(
            TextGraphics g,
            int x, int y, int w, int h,
            String[] labels,
            double[] values,
            double max,
            String[] formatted,
            TextColor color) {

        if (w < 10 || h < 1 || labels == null || labels.length == 0) return;

        // Determine the label column width (longest label, capped at 1/4 of total width)
        int labelW = 0;
        for (String lbl : labels) {
            if (lbl != null) labelW = Math.max(labelW, lbl.length());
        }
        labelW = Math.min(labelW, w / 4);

        // Determine value column width
        int valueW = 0;
        if (formatted != null) {
            for (String fmt : formatted) {
                if (fmt != null) valueW = Math.max(valueW, fmt.length());
            }
        }
        valueW = Math.min(valueW, 10);

        int barW = w - labelW - valueW - 2; // 2 spaces as separators

        int rows = Math.min(h, labels.length);
        for (int row = 0; row < rows; row++) {
            int ry = y + row;
            String lbl = labels[row] != null ? labels[row] : "";
            double val = (values != null && row < values.length) ? values[row] : 0;
            String fmt = (formatted != null && row < formatted.length) ? formatted[row] : null;

            // Label
            Theme.put(g, x, ry, labelW, lbl, Theme.INK_SECONDARY);

            // Bar
            if (barW > 0 && max > 0) {
                int col = x + labelW + 1;
                int filled = (int) Math.round(barW * Math.max(0, Math.min(max, val)) / max);
                if (filled > 0)
                    Theme.put(g, col, ry, filled, FULL.repeat(filled), color);
                if (filled < barW)
                    Theme.put(g, col + filled, ry, barW - filled, EMPTY.repeat(barW - filled), Theme.BASELINE);
            }

            // Value
            if (fmt != null && valueW > 0) {
                Theme.put(g, x + labelW + 1 + barW + 1, ry, valueW, fmt, color, true);
            }
        }
    }
}
