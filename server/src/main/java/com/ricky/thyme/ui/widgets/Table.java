package com.ricky.thyme.ui.widgets;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.ricky.thyme.ui.Theme;
import java.util.List;

/**
 * A scrollable table widget with optional column headers and row selection.
 *
 * <p>Renders a list of rows where each row is an array of column values.
 * The selected row is highlighted in {@link Theme#RAISED}/{@link Theme#ACCENT}.
 *
 * <p>This widget is <em>render-only</em> and holds no domain state.
 */
public final class Table {

    private Table() {}

    /**
     * Draws the table inside the given rectangle.
     *
     * @param g             graphics context
     * @param x             left column
     * @param y             top row
     * @param w             width in columns
     * @param h             height in rows (including optional header)
     * @param headers       column header labels (may be {@code null} to skip the header row)
     * @param rows          table rows; each row is an array of cell strings
     * @param colWidths     width for each column; if {@code null} the width is split evenly
     * @param selectedRow   index of the selected row (0-based); negative to disable
     * @param scrollOffset  first visible row index (for scrolling)
     */
    public static void draw(
            TextGraphics g,
            int x, int y, int w, int h,
            String[] headers,
            List<String[]> rows,
            int[] colWidths,
            int selectedRow,
            int scrollOffset) {

        if (w < 4 || h < 1 || rows == null) return;

        int[] cw = resolveColWidths(colWidths, headers, rows, w);
        int row = 0;

        // Header
        if (headers != null && h > 1) {
            drawRow(g, x, y, w, headers, cw, false, false);
            // Separator
            Theme.put(g, x, y + 1, w, String.valueOf(Theme.H).repeat(w), Theme.HAIRLINE);
            row = 2;
        }

        // Data rows
        int visibleRows = h - row;
        int dataStart = Math.max(0, scrollOffset);
        for (int i = dataStart; i < rows.size() && (row < h); i++, row++) {
            boolean selected = (i == selectedRow);
            drawRow(g, x, y + row, w, rows.get(i), cw, selected, true);
        }
    }

    // -------------------------------------------------------------------------

    private static void drawRow(
            TextGraphics g,
            int x, int y, int totalW,
            String[] cells,
            int[] colWidths,
            boolean selected,
            boolean isData) {

        if (cells == null) return;
        int col = x;
        for (int c = 0; c < colWidths.length && col < x + totalW; c++) {
            String cell = (c < cells.length && cells[c] != null) ? cells[c] : "";
            int cw = Math.min(colWidths[c], x + totalW - col);
            if (selected) {
                Theme.put(g, col, y, cw, cell, Theme.ACCENT, true);
            } else if (isData) {
                Theme.put(g, col, y, cw, cell, Theme.INK_SECONDARY);
            } else {
                Theme.put(g, col, y, cw, cell, Theme.INK_MUTED, true);
            }
            col += cw + 1; // 1-column separator
        }
    }

    private static int[] resolveColWidths(int[] supplied, String[] headers, List<String[]> rows, int totalW) {
        if (supplied != null && supplied.length > 0) return supplied;

        // Determine column count
        int cols = 0;
        if (headers != null) cols = headers.length;
        for (String[] row : rows) if (row != null) cols = Math.max(cols, row.length);
        if (cols == 0) return new int[]{totalW};

        // Split evenly (last column gets remainder)
        int[] cw = new int[cols];
        int base = (totalW - cols + 1) / cols;
        for (int i = 0; i < cols; i++) cw[i] = base;
        cw[cols - 1] = totalW - (cols - 1) * (base + 1);
        return cw;
    }
}
