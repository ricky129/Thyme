package com.ricky.thyme.ui.widgets;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.ricky.thyme.ui.Theme;

/**
 * A horizontal bar gauge widget.
 *
 * <p>Renders a filled/unfilled track with a label on the left and a value
 * on the right:
 *
 * <pre>
 *   CPU  ████████████░░░░  42%
 * </pre>
 *
 * <p>This widget is <em>render-only</em> and holds no domain state.
 */
public final class Gauge {

    private static final String FULL  = "█";
    private static final String EMPTY = "░";

    private Gauge() {}

    /**
     * Draws the gauge at {@code (x, y)} within {@code width} columns.
     *
     * @param g          graphics context
     * @param x          left column (0-based)
     * @param y          row (0-based)
     * @param width      total width available (must be ≥ 6 for meaningful output)
     * @param label      text on the left (e.g. {@code "CPU"})
     * @param value      current value in [0, 100], or {@code null} for n/a
     * @param formatted  pre-formatted value string (e.g. {@code "42%"} or {@code "n/a"})
     * @param barColor   colour for the filled portion of the bar
     */
    public static void draw(
            TextGraphics g,
            int x, int y, int width,
            String label,
            Double value,
            String formatted,
            TextColor barColor) {

        if (width <= 0) return;
        if (width < 6) {
            // Not enough space — just emit the label and value
            String compact = (label != null ? label + " " : "") + (formatted != null ? formatted : "n/a");
            Theme.put(g, x, y, width, compact, barColor);
            return;
        }

        // Layout: [label] [space] [bar...] [space] [value]
        int labelLen = label != null ? label.length() : 0;
        int valueLen = formatted != null ? formatted.length() : 3;

        // Minimum bar width = 4; reduce label or skip if too tight
        int barWidth = width - labelLen - valueLen - 2; // 2 spaces as separators
        if (barWidth < 4) {
            // Try skipping the label
            barWidth = width - valueLen - 1;
            labelLen = 0;
        }
        if (barWidth < 2) {
            Theme.put(g, x, y, width, formatted != null ? formatted : "n/a", barColor);
            return;
        }

        int col = x;

        // Label
        if (labelLen > 0 && label != null) {
            Theme.put(g, col, y, labelLen, label, Theme.INK_SECONDARY, true);
            col += labelLen + 1;
        }

        // Bar
        int filled = value == null ? 0 : (int) Math.round(barWidth * Math.max(0, Math.min(100, value)) / 100.0);
        if (filled > 0) {
            Theme.put(g, col, y, filled, FULL.repeat(filled), barColor);
        }
        if (filled < barWidth) {
            Theme.put(g, col + filled, y, barWidth - filled, EMPTY.repeat(barWidth - filled), Theme.BASELINE);
        }
        col += barWidth + 1;

        // Value
        if (formatted != null) {
            Theme.put(g, col, y, valueLen, formatted, barColor, true);
        }
    }
}
