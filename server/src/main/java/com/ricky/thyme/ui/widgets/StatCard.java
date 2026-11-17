package com.ricky.thyme.ui.widgets;

import com.googlecode.lanterna.graphics.TextGraphics;
import com.ricky.thyme.ui.Theme;

/**
 * A KPI card widget — a small bordered tile showing a metric name, a large
 * value, an inline sparkline and a caption line underneath.
 *
 * <p>This widget is <em>render-only</em> and holds no domain state.
 *
 * <h2>Layout (example, 20×4)</h2>
 * <pre>
 *   ╭──────────────────╮
 *   │ CPU %  › ▄▆█▃▅▇  │
 *   │ 42%               │
 *   ╰──────────────────╯
 * </pre>
 */
public final class StatCard {

    /** Block-character sparkline glyphs, low-to-high. */
    private static final char[] SPARK = {'▁', '▂', '▃', '▄', '▅', '▆', '▇', '█'};

    private StatCard() {}

    /**
     * Draws the stat card at the given position and size.
     *
     * @param g          graphics context
     * @param x          left column (0-based)
     * @param y          top row (0-based)
     * @param w          width in columns (minimum 6)
     * @param h          height in rows (minimum 3)
     * @param label      metric name shown in the top-left of the interior
     * @param value      formatted value string (e.g. {@code "42%"} or {@code "n/a"})
     * @param valueColor colour for the value string; use {@link Theme#utilizationColor} etc.
     * @param history    recent samples in [0, 100] for the sparkline; may be empty
     * @param caption    muted caption shown below the value (may be {@code null})
     * @param drillable  if {@code true}, appends {@code ›} to the label
     */
    public static void draw(
            TextGraphics g,
            int x, int y, int w, int h,
            String label,
            String value,
            com.googlecode.lanterna.TextColor valueColor,
            double[] history,
            String caption,
            boolean drillable) {
        draw(g, x, y, w, h, label, value, valueColor, history, caption, drillable, false);
    }

    /** Draws a card with an accent border when it is the current keyboard selection. */
    public static void draw(
            TextGraphics g,
            int x, int y, int w, int h,
            String label,
            String value,
            com.googlecode.lanterna.TextColor valueColor,
            double[] history,
            String caption,
            boolean drillable,
            boolean selected) {

        if (w < 4 || h < 2) return;

        // Border
        Theme.drawPanel(g, x, y, w, h, selected ? Theme.ACCENT : Theme.HAIRLINE);

        int ix = Panel.interiorX(x);
        int iy = Panel.interiorY(y);
        int iw = Panel.interiorW(w);
        int ih = Panel.interiorH(h);

        if (iw <= 0 || ih <= 0) return;

        // Row 0: label [›] [sparkline]
        String labelStr = drillable ? (label + " ›") : label;
        int labelLen = Math.min(labelStr.length(), iw);
        Theme.put(g, ix, iy, labelLen, labelStr, Theme.INK_SECONDARY, true);

        // Sparkline inline to the right of label
        int sparkWidth = iw - labelLen - 1;
        if (sparkWidth > 1 && history != null && history.length > 0) {
            String spark = buildSparkline(history, sparkWidth);
            Theme.put(g, ix + labelLen + 1, iy, sparkWidth, spark, Theme.PRIMARY);
        }

        // Row 1: value (large)
        if (ih >= 2) {
            Theme.put(g, ix, iy + 1, iw, value != null ? value : "n/a", valueColor, true);
        }

        // Row 2: caption (muted)
        if (ih >= 3 && caption != null) {
            Theme.put(g, ix, iy + 2, iw, caption, Theme.INK_MUTED);
        }
    }

    /**
     * Builds a sparkline string of exactly {@code width} characters using
     * block elements.  Values outside [0, 100] are clamped.
     *
     * @param history samples (most recent last)
     * @param width   number of terminal columns to fill
     * @return sparkline string of length {@code width}
     */
    public static String buildSparkline(double[] history, int width) {
        if (width <= 0 || history == null || history.length == 0)
            return "";
        int start = Math.max(0, history.length - width);
        StringBuilder sb = new StringBuilder(width);
        for (int i = start; i < history.length; i++) {
            double v = Math.max(0, Math.min(100, history[i]));
            sb.append(SPARK[(int) Math.round(v / 100.0 * (SPARK.length - 1))]);
        }
        // Pad left with the lowest glyph if fewer samples than width
        while (sb.length() < width)
            sb.insert(0, SPARK[0]);
        return sb.toString();
    }
}
