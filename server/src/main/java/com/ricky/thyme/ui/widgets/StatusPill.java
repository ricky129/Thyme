package com.ricky.thyme.ui.widgets;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.ricky.thyme.ui.Theme;

/**
 * A status indicator pill: a coloured dot ({@code ●}) followed by optional
 * label text.
 *
 * <p>Example: {@code ● good}, {@code ● warn}, {@code ● crit}.
 *
 * <p>This widget is <em>render-only</em> and holds no domain state.
 */
public final class StatusPill {

    /** Unicode filled-circle glyph used as the status dot. */
    public static final String DOT = "●";

    private StatusPill() {}

    /**
     * Draws the status pill at {@code (x, y)}.
     *
     * @param g      graphics context
     * @param x      column (0-based)
     * @param y      row (0-based)
     * @param width  maximum columns to use (must be ≥ 1)
     * @param color  dot colour ({@link Theme#GOOD}, {@link Theme#WARM} or
     *               {@link Theme#CRITICAL})
     * @param label  optional text after the dot (may be {@code null})
     */
    public static void draw(
            TextGraphics g, int x, int y, int width, TextColor color, String label) {
        if (width <= 0) return;
        g.setForegroundColor(color);
        g.putString(x, y, DOT);
        if (label != null && !label.isEmpty() && width > 2) {
            Theme.put(g, x + 2, y, width - 2, label, Theme.INK_SECONDARY);
        }
    }

    /**
     * Returns the colour corresponding to a CPU/RAM/GPU utilisation value.
     * Convenience shorthand for {@link Theme#utilizationColor(Double)}.
     */
    public static TextColor colorForUtilization(Double percent) {
        return Theme.utilizationColor(percent);
    }
}
