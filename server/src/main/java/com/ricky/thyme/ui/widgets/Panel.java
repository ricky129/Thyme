package com.ricky.thyme.ui.widgets;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.ricky.thyme.ui.Theme;

/**
 * A bordered, titled panel widget.
 *
 * <p>Draws a rounded border ({@code ╭╮╰╯─│}) using {@link Theme#HAIRLINE}
 * and places an optional title inline in the top border.  The interior
 * region (inside the border) is left for the caller to populate.
 *
 * <p>This widget is <em>render-only</em>: it holds no domain state.
 *
 * <h2>Layout</h2>
 * <pre>
 *   ╭─ Title ─────────────────╮
 *   │                         │
 *   │   &lt;interior&gt;            │
 *   │                         │
 *   ╰─────────────────────────╯
 * </pre>
 */
public final class Panel {

    private Panel() {}

    /**
     * Draws the panel border at the given position and size.
     *
     * @param g     graphics context
     * @param x     left column (0-based, inclusive)
     * @param y     top row    (0-based, inclusive)
     * @param w     width  in columns (must be ≥ 2)
     * @param h     height in rows    (must be ≥ 2)
     * @param title optional title placed in the top border (may be {@code null})
     */
    public static void draw(TextGraphics g, int x, int y, int w, int h, String title) {
        draw(g, x, y, w, h, title, Theme.HAIRLINE);
    }

    /**
     * Draws the panel border with an explicit border colour.
     */
    public static void draw(
            TextGraphics g, int x, int y, int w, int h, String title, TextColor borderColor) {
        TextColor effectiveBorder = borderColor != null ? borderColor : Theme.HAIRLINE;
        Theme.drawPanel(g, x, y, w, h, effectiveBorder);
        if (title != null && !title.isEmpty() && w > 4) {
            // Embed "─ Title ─" into the top border row
            String label = " " + title + " ";
            String clipped = Theme.clipToWidth(label, w - 4);
            TextColor titleColor = Theme.ACCENT.equals(effectiveBorder)
                    ? Theme.ACCENT : Theme.INK_SECONDARY;
            Theme.put(g, x + 2, y, clipped.length(), clipped, titleColor, true);
        }
    }

    /**
     * Returns the interior region (column offset, row offset, width, height)
     * available inside the border drawn by {@link #draw}.
     *
     * @param x outer x
     * @param y outer y
     * @param w outer width
     * @param h outer height
     * @return a {@link TerminalSize} representing the interior dimensions,
     *         or a zero-area size if the panel is too small
     */
    public static int interiorX(int x) { return x + 1; }
    public static int interiorY(int y) { return y + 1; }
    public static int interiorW(int w) { return Math.max(0, w - 2); }
    public static int interiorH(int h) { return Math.max(0, h - 2); }
}
