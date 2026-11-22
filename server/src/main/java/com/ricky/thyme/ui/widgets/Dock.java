package com.ricky.thyme.ui.widgets;

import com.googlecode.lanterna.graphics.TextGraphics;
import com.ricky.thyme.ui.Theme;

/**
 * A navigational footer bar ("dock") showing the available pages.
 *
 * <p>Example (selected index = 0):
 * <pre>
 *   [1 Overview] 2 Storage  3 Graphics  4 Services  5 Containers
 * </pre>
 *
 * <p>This widget is <em>render-only</em> and holds no domain state.
 */
public final class Dock {

    private Dock() {}

    /**
     * Draws the dock at {@code (x, y)} within {@code width} columns.
     *
     * @param g             graphics context
     * @param x             left column
     * @param y             row
     * @param width         maximum width
     * @param labels        page labels (e.g. {@code ["Overview", "Storage", ...]})
     * @param selectedIndex index of the currently-active page
     */
    public static void draw(
            TextGraphics g,
            int x, int y, int width,
            String[] labels,
            int selectedIndex) {

        if (labels == null || labels.length == 0 || width <= 0) return;

        int col = x;
        for (int i = 0; i < labels.length; i++) {
            if (col >= x + width) break;
            boolean selected = i == selectedIndex;
            String entry = (i + 1) + " " + labels[i];
            int remaining = x + width - col;
            if (selected) {
                Theme.put(g, col, y, Math.min(entry.length(), remaining), entry, Theme.ACCENT, true);
            } else {
                Theme.put(g, col, y, Math.min(entry.length(), remaining), entry, Theme.INK_MUTED);
            }
            col += entry.length();
            if (i < labels.length - 1 && col < x + width - 2) {
                Theme.put(g, col, y, 2, "  ", Theme.INK_MUTED);
                col += 2;
            }
        }
    }

    /**
     * Returns the index of the dock entry at {@code (clickCol, clickRow)}, or {@code -1}
     * if the coordinates do not fall on a rendered entry.
     */
    public static int hitTest(
            int x, int y, int width,
            String[] labels,
            int clickCol, int clickRow) {
        if (clickRow != y || labels == null || labels.length == 0 || width <= 0) {
            return -1;
        }
        int col = x;
        for (int i = 0; i < labels.length; i++) {
            if (col >= x + width) {
                break;
            }
            String entry = (i + 1) + " " + labels[i];
            int remaining = x + width - col;
            int renderedLen = Math.min(entry.length(), remaining);
            if (clickCol >= col && clickCol < col + renderedLen) {
                return i;
            }
            col += entry.length();
            if (i < labels.length - 1 && col < x + width - 2) {
                col += 2;
            }
        }
        return -1;
    }
}
