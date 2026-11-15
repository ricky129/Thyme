package com.ricky.thyme.ui.widgets;

import com.googlecode.lanterna.graphics.TextGraphics;
import com.ricky.thyme.ui.Theme;

/**
 * A segmented-tabs bar for switching between named intervals or views.
 *
 * <p>Example (selected index = 1):
 * <pre>
 *   15M  [1H]  6H
 * </pre>
 *
 * <p>This widget is <em>render-only</em> and holds no domain state.
 */
public final class SegmentedTabs {

    private SegmentedTabs() {}

    /**
     * Draws the tab bar at {@code (x, y)} within {@code width} columns.
     *
     * @param g             graphics context
     * @param x             left column
     * @param y             row
     * @param width         maximum width available
     * @param labels        tab labels (e.g. {@code ["15M", "1H", "6H"]})
     * @param selectedIndex index of the currently-selected tab
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
            String tab = " " + labels[i] + " ";
            int remaining = x + width - col;
            if (selected) {
                Theme.put(g, col, y, Math.min(tab.length(), remaining), tab, Theme.ACCENT, true);
            } else {
                Theme.put(g, col, y, Math.min(tab.length(), remaining), tab, Theme.INK_MUTED);
            }
            col += tab.length();
        }
    }
}
