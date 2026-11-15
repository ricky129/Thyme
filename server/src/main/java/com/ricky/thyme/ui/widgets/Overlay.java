package com.ricky.thyme.ui.widgets;

import com.googlecode.lanterna.graphics.TextGraphics;
import com.ricky.thyme.ui.Theme;
import java.util.List;

/**
 * A modal overlay widget — a floating panel drawn on top of the main content.
 *
 * <p>Used for drill-in detail panels, confirmations and help pages.  The
 * overlay draws a rounded border, a title and scrollable text content.
 *
 * <p>This widget is <em>render-only</em> and holds no domain state.
 */
public final class Overlay {

    private Overlay() {}

    /**
     * Draws the overlay centred within the terminal at the given size.
     *
     * @param g       graphics context for the full terminal
     * @param termW   terminal width in columns
     * @param termH   terminal height in rows
     * @param maxW    maximum overlay width (actual width is min of this and termW - 4)
     * @param maxH    maximum overlay height (actual height is min of this and termH - 2)
     * @param title   overlay title
     * @param lines   content lines to display (scrolled by {@code scrollOffset})
     * @param scrollOffset first visible line index
     * @param footer  optional footer hint (e.g. {@code "Esc close · ↑↓ scroll"})
     */
    public static void draw(
            TextGraphics g,
            int termW, int termH,
            int maxW, int maxH,
            String title,
            List<String> lines,
            int scrollOffset,
            String footer) {

        int ow = Math.min(maxW, termW - 4);
        int oh = Math.min(maxH, termH - 2);
        if (ow < 6 || oh < 4) return;

        // Centre the overlay
        int ox = (termW - ow) / 2;
        int oy = (termH - oh) / 2;

        // Border + title
        Panel.draw(g, ox, oy, ow, oh, title);

        int ix = Panel.interiorX(ox);
        int iy = Panel.interiorY(oy);
        int iw = Panel.interiorW(ow);
        int ih = Panel.interiorH(oh) - (footer != null ? 1 : 0);

        // Content lines
        if (lines != null) {
            int start = Math.max(0, scrollOffset);
            int row = 0;
            for (int i = start; i < lines.size() && row < ih; i++, row++) {
                Theme.put(g, ix, iy + row, iw, lines.get(i), Theme.INK_SECONDARY);
            }
        }

        // Footer hint
        if (footer != null) {
            Theme.put(g, ix, iy + ih, iw, footer, Theme.INK_MUTED);
        }
    }
}
