package com.ricky.thyme.ui.widgets;

import com.googlecode.lanterna.graphics.TextGraphics;

/**
 * An inline sparkline drawn entirely with Unicode block elements.
 *
 * <p>When the terminal does not support braille (detected by an absent
 * {@code TERM} capability or by the caller passing {@code braille=false}),
 * the widget falls back to block glyphs {@code ▁▂▃▄▅▆▇█}.
 *
 * <p>This widget is <em>render-only</em> and holds no domain state.
 */
public final class Sparkline {

    /** Block-element glyphs, lowest-to-highest. */
    static final char[] BLOCK = {'▁', '▂', '▃', '▄', '▅', '▆', '▇', '█'};

    /**
     * Braille dots encoding for a 2-column × 4-row braille cell.
     * Each cell encodes two columns of four bits each (left col, right col).
     * Bit 0 = top, bit 3 = bottom of each column.
     */
    private Sparkline() {}

    /**
     * Draws the sparkline in the available width.
     *
     * @param g       graphics context
     * @param x       left column
     * @param y       row
     * @param width   available columns (the sparkline occupies all of them)
     * @param history samples in [0, 100]; most recent last; may be empty
     * @param color   foreground colour
     * @param braille {@code true} to use braille (2× horizontal resolution),
     *                {@code false} to always use block glyphs
     */
    public static void draw(
            TextGraphics g,
            int x, int y, int width,
            double[] history,
            com.googlecode.lanterna.TextColor color,
            boolean braille) {

        if (width <= 0 || history == null || history.length == 0) return;

        g.setForegroundColor(color);
        String line = braille
                ? buildBraille(history, width)
                : buildBlock(history, width);
        g.putString(x, y, line);
    }

    /**
     * Builds a block-element sparkline string of exactly {@code width}
     * characters.  Samples outside [0, 100] are clamped.
     *
     * @param history samples in [0, 100]; most recent last
     * @param width   number of characters to produce
     * @return sparkline of length {@code width}
     */
    public static String buildBlock(double[] history, int width) {
        if (width <= 0 || history == null || history.length == 0)
            return " ".repeat(Math.max(0, width));
        int start = Math.max(0, history.length - width);
        StringBuilder sb = new StringBuilder(width);
        // Pad left if fewer samples than width
        int pad = width - (history.length - start);
        for (int i = 0; i < pad; i++) sb.append(BLOCK[0]);
        for (int i = start; i < history.length; i++) {
            double v = Math.max(0, Math.min(100, history[i]));
            sb.append(BLOCK[(int) Math.round(v / 100.0 * (BLOCK.length - 1))]);
        }
        return sb.toString();
    }

    /**
     * Builds a braille sparkline.  Each terminal column holds two data points
     * (left and right half of a braille cell), so the effective data capacity
     * is {@code 2 * width} samples.
     *
     * <p>Only the bottom four rows of the braille cell (dots 1-4 on each side)
     * are used as a single-row sparkline; each column covers values in
     * [0, 100] mapped to 4 vertical levels (0 dots lit = silent, 4 = full).
     *
     * @param history samples in [0, 100]; most recent last
     * @param width   number of terminal columns to produce
     * @return string of exactly {@code width} braille code-points
     */
    public static String buildBraille(double[] history, int width) {
        if (width <= 0 || history == null || history.length == 0)
            return " ".repeat(Math.max(0, width));

        // Each terminal column holds two samples (left and right half-cell).
        int capacity = width * 2;

        // Build a virtual padded buffer of size 'capacity': pad with 0.0 on the left,
        // then append the most-recent samples (at most 'capacity' of them).
        int histStart = Math.max(0, history.length - capacity);
        int histCount = history.length - histStart;   // ≤ capacity
        int padCount  = capacity - histCount;          // leading virtual zeros

        StringBuilder sb = new StringBuilder(width);
        for (int col = 0; col < width; col++) {
            int leftPos  = col * 2;
            int rightPos = col * 2 + 1;

            double leftVal  = valueAt(history, histStart, padCount, leftPos);
            double rightVal = valueAt(history, histStart, padCount, rightPos);

            int leftDots  = sampleToDots(leftVal,  leftPos  >= padCount);
            int rightDots = sampleToDots(rightVal, rightPos >= padCount);

            sb.appendCodePoint(0x2800 + encodeBraille(leftDots, rightDots));
        }
        return sb.toString();
    }

    /** Returns the value at virtual buffer position {@code pos} (0 = oldest pad slot). */
    private static double valueAt(
            double[] history, int histStart, int padCount, int pos) {
        if (pos < padCount) return 0.0; // padding
        int idx = histStart + pos - padCount;
        if (idx < history.length) return history[idx];
        return 0.0;
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Maps a [0, 100] sample to a number of lit dots (0–4) in the braille
     * column.  Returns 0 if the sample index is out of range.
     */
    private static int sampleToDots(double value, boolean valid) {
        if (!valid) return 0;
        double clamped = Math.max(0, Math.min(100, value));
        return (int) Math.round(clamped / 100.0 * 4);
    }

    /**
     * Encodes left-column dots and right-column dots into the Unicode braille
     * offset.  Braille dot numbering:
     * <pre>
     *   dots 1 (top-left)   = bit 0
     *   dots 2              = bit 1
     *   dots 3              = bit 2
     *   dots 7 (bottom-left)= bit 6  (unused — only 4 rows)
     *   dots 4 (top-right)  = bit 3
     *   dots 5              = bit 4
     *   dots 6              = bit 5
     *   dots 8 (bottom-right)= bit 7 (unused)
     * </pre>
     * We fill from the bottom up: dots 3→2→1 for 3 lit, etc.
     */
    private static int encodeBraille(int leftDots, int rightDots) {
        // Left column uses bits 0-3 (braille dots 1-4, bottom-up)
        // Right column uses bits 4-7 would be dots 4-7, but standard braille
        // right-column bits for a 2×4 cell (dots 1-8) are:
        //   dot 1 bit 0, dot 2 bit 1, dot 3 bit 2, dot 4 bit 6
        //   dot 5 bit 3, dot 6 bit 4, dot 7 bit 5, dot 8 bit 7
        // For our simple 1-row sparkline approach (4 levels per column):
        // left dots: standard bits 0,1,2,6 (dots 1,2,3,7)
        // right dots: standard bits 3,4,5,7 (dots 4,5,6,8)
        int[] leftBits  = {0x01, 0x02, 0x04, 0x40};
        int[] rightBits = {0x08, 0x10, 0x20, 0x80};

        int enc = 0;
        for (int i = 0; i < leftDots  && i < 4; i++) enc |= leftBits[i];
        for (int i = 0; i < rightDots && i < 4; i++) enc |= rightBits[i];
        return enc;
    }
}
