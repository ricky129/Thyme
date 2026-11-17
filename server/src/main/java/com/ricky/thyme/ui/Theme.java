package com.ricky.thyme.ui;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.SGR;
import java.util.Locale;

/**
 * Central palette, thresholds, border characters and colour-capability
 * detection for the Thyme TUI.
 *
 * <p>All colour constants are defined here once; no other class should
 * instantiate {@link TextColor.RGB} directly.  When {@code NO_COLOR} is set
 * or the terminal does not advertise truecolor the class falls back to 256-
 * colour ANSI approximations, then to 16-colour ANSI.</p>
 *
 * <h2>Colour roles</h2>
 * <pre>
 *   page        – terminal background (black)
 *   surface     – card / panel background
 *   raised      – hover / selection background
 *   hairline    – borders
 *   baseline    – axes, empty bar tracks
 *   ink         – primary text
 *   inkSecondary – secondary / label text
 *   inkMuted    – de-emphasised / caption text
 *   primary     – primary series / download sparkline
 *   secondary   – secondary series / upload sparkline
 *   warm        – warning state
 *   accent      – focused / selected element
 *   good        – healthy status
 *   critical    – critical / error status
 * </pre>
 *
 * <h2>Thresholds</h2>
 * <pre>
 *   CPU / RAM / GPU  : warning ≥ 80 %, critical ≥ 95 %
 *   Filesystem       : warning ≥ 85 %, critical ≥ 95 %
 *   Temperature      : warning ≥ 80 °C, critical ≥ 90 °C
 *   Service failed   : always critical
 * </pre>
 *
 * <h2>Border characters</h2>
 * Rounded box-drawing set: {@code ╭ ╮ ╰ ╯ ─ │}.
 */
public final class Theme {

    // -------------------------------------------------------------------------
    // Capability detection
    // -------------------------------------------------------------------------

    /** Whether the terminal supports 24-bit (truecolor) colour. */
    public static final boolean TRUECOLOR;
    /** Whether the terminal supports the 256-colour indexed palette. */
    public static final boolean INDEXED_256;
    /** Whether colour output is suppressed (NO_COLOR env var is set). */
    public static final boolean NO_COLOR;

    static {
        NO_COLOR = System.getenv("NO_COLOR") != null;
        String colorterm = System.getenv("COLORTERM");
        String term = System.getenv("TERM");
        String normalizedTerm = term == null ? "" : term.toLowerCase(Locale.ROOT);
        TRUECOLOR = !NO_COLOR
                && ((colorterm != null && ("truecolor".equalsIgnoreCase(colorterm)
                        || "24bit".equalsIgnoreCase(colorterm)))
                        || normalizedTerm.contains("direct"));
        INDEXED_256 = !NO_COLOR && !TRUECOLOR && normalizedTerm.contains("256color");
    }

    // -------------------------------------------------------------------------
    // Palette – truecolor values (Dishylink-derived)
    // -------------------------------------------------------------------------

    public static final TextColor PAGE        = color(0x00, 0x00, 0x00, TextColor.ANSI.BLACK);
    public static final TextColor SURFACE     = color(0x0c, 0x0c, 0x0c, TextColor.ANSI.BLACK);
    public static final TextColor RAISED      = color(0x1e, 0x1e, 0x1e, TextColor.ANSI.BLACK_BRIGHT);
    public static final TextColor HAIRLINE    = color(0x23, 0x23, 0x23, TextColor.ANSI.BLACK_BRIGHT);
    public static final TextColor BASELINE    = color(0x3a, 0x3a, 0x3a, TextColor.ANSI.BLACK_BRIGHT);
    public static final TextColor INK         = color(0xff, 0xff, 0xff, TextColor.ANSI.WHITE_BRIGHT);
    public static final TextColor INK_SECONDARY = color(0xb9, 0xb9, 0xb9, TextColor.ANSI.WHITE);
    public static final TextColor INK_MUTED   = color(0x7c, 0x7c, 0x7c, TextColor.ANSI.BLACK_BRIGHT);
    public static final TextColor PRIMARY     = color(0x5b, 0x9b, 0xff, TextColor.ANSI.BLUE_BRIGHT);
    public static final TextColor SECONDARY   = color(0x4a, 0xde, 0x80, TextColor.ANSI.GREEN_BRIGHT);
    public static final TextColor CPU_SERIES  = color(0x7d, 0xd3, 0xfc, TextColor.ANSI.CYAN_BRIGHT);
    public static final TextColor MEMORY_SERIES = PRIMARY;
    public static final TextColor IO_SERIES   = SECONDARY;
    public static final TextColor SWAP_SERIES = color(0x93, 0xc5, 0xfd, TextColor.ANSI.BLUE);
    public static final TextColor WARM        = color(0xf0, 0x90, 0x48, TextColor.ANSI.YELLOW);
    public static final TextColor ACCENT      = color(0xe0, 0xa4, 0x22, TextColor.ANSI.YELLOW_BRIGHT);
    public static final TextColor GOOD        = color(0x4d, 0xb4, 0x50, TextColor.ANSI.GREEN);
    public static final TextColor CRITICAL    = color(0xf4, 0x3f, 0x5e, TextColor.ANSI.RED_BRIGHT);

    // -------------------------------------------------------------------------
    // Thresholds
    // -------------------------------------------------------------------------

    /** Warning threshold for CPU / RAM / GPU utilisation (%). */
    public static final double CPU_RAM_GPU_WARN = 80.0;
    /** Critical threshold for CPU / RAM / GPU utilisation (%). */
    public static final double CPU_RAM_GPU_CRIT = 95.0;

    /** Warning threshold for filesystem utilisation (%). */
    public static final double FS_WARN = 85.0;
    /** Critical threshold for filesystem utilisation (%). */
    public static final double FS_CRIT = 95.0;

    /** Warning threshold for temperature (°C). */
    public static final double TEMP_WARN = 80.0;
    /** Critical threshold for temperature (°C). */
    public static final double TEMP_CRIT = 90.0;

    // -------------------------------------------------------------------------
    // Border characters (rounded set)
    // -------------------------------------------------------------------------

    public static final char TL = '╭'; // top-left
    public static final char TR = '╮'; // top-right
    public static final char BL = '╰'; // bottom-left
    public static final char BR = '╯'; // bottom-right
    public static final char H  = '─'; // horizontal
    public static final char V  = '│'; // vertical

    // -------------------------------------------------------------------------
    // Semantic colour helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the colour for a CPU / RAM / GPU utilisation value.
     *
     * @param percent utilisation in [0, 100], or {@code null} if unavailable
     * @return {@link #CRITICAL}, {@link #WARM} or {@link #INK}
     */
    public static TextColor utilizationColor(Double percent) {
        if (percent == null)   return INK_MUTED;
        if (percent >= CPU_RAM_GPU_CRIT) return CRITICAL;
        if (percent >= CPU_RAM_GPU_WARN) return WARM;
        return INK;
    }

    /**
     * Returns the colour for a filesystem utilisation value.
     *
     * @param percent utilisation in [0, 100], or {@code null} if unavailable
     * @return {@link #CRITICAL}, {@link #WARM} or {@link #INK}
     */
    public static TextColor filesystemColor(Double percent) {
        if (percent == null)   return INK_MUTED;
        if (percent >= FS_CRIT)  return CRITICAL;
        if (percent >= FS_WARN)  return WARM;
        return INK;
    }

    /**
     * Returns the colour for a temperature value.
     *
     * @param celsius temperature in °C, or {@code null} if unavailable
     * @return {@link #CRITICAL}, {@link #WARM} or {@link #INK}
     */
    public static TextColor temperatureColor(Double celsius) {
        if (celsius == null)   return INK_MUTED;
        if (celsius >= TEMP_CRIT) return CRITICAL;
        if (celsius >= TEMP_WARN) return WARM;
        return INK;
    }

    // -------------------------------------------------------------------------
    // Drawing helpers
    // -------------------------------------------------------------------------

    /**
     * Writes {@code text} at {@code (x, y)}, clipping to {@code maxWidth}
     * columns.  Uses code-point length rather than char-count so that
     * single-width Unicode characters are handled correctly.  Multi-width
     * (CJK, emoji) characters are not truncated mid-glyph: truncation stops
     * at the last code point that fits.
     *
     * @param g        graphics context
     * @param x        column (0-based)
     * @param y        row (0-based)
     * @param maxWidth maximum number of terminal columns to occupy
     * @param text     text to draw (may be {@code null} — treated as empty)
     * @param color    foreground colour
     */
    public static void put(TextGraphics g, int x, int y, int maxWidth, String text, TextColor color) {
        put(g, x, y, maxWidth, text, color, false);
    }

    /**
     * Like {@link #put(TextGraphics, int, int, int, String, TextColor)} but
     * also applies {@link SGR#BOLD} when {@code bold} is {@code true}.
     */
    public static void put(
            TextGraphics g, int x, int y, int maxWidth, String text, TextColor color, boolean bold) {
        if (maxWidth <= 0 || text == null || text.isEmpty()) return;
        String clipped = clipToWidth(text, maxWidth);
        g.setForegroundColor(color);
        if (bold)
            g.putString(x, y, clipped, SGR.BOLD);
        else
            g.putString(x, y, clipped);
    }

    /**
     * Clips {@code text} to at most {@code maxWidth} terminal columns,
     * appending {@code …} if truncation occurred.  Counts code points
     * (not chars) so basic BMP characters work correctly.
     */
    public static String clipToWidth(String text, int maxWidth) {
        if (text == null) return "";
        if (maxWidth <= 0) return "";
        int[] codePoints = text.codePoints().toArray();
        if (codePoints.length <= maxWidth) return text;
        if (maxWidth == 1) return new String(codePoints, 0, 1);
        // Reserve one column for the ellipsis
        int take = maxWidth - 1;
        return new String(codePoints, 0, take) + "…";
    }

    /**
     * Draws a rounded panel border at the given position and size.
     * The interior is not cleared.
     *
     * @param g      graphics context
     * @param x      left column of the panel
     * @param y      top row of the panel
     * @param w      width in columns (must be ≥ 2)
     * @param h      height in rows (must be ≥ 2)
     * @param color  border colour (typically {@link #HAIRLINE})
     */
    public static void drawPanel(TextGraphics g, int x, int y, int w, int h, TextColor color) {
        if (w < 2 || h < 2) return;
        g.setForegroundColor(color);
        // Top row
        g.putString(x, y, String.valueOf(TL));
        if (w > 2)
            g.putString(x + 1, y, String.valueOf(H).repeat(w - 2));
        g.putString(x + w - 1, y, String.valueOf(TR));
        // Side columns
        for (int row = y + 1; row < y + h - 1; row++) {
            g.putString(x, row, String.valueOf(V));
            g.putString(x + w - 1, row, String.valueOf(V));
        }
        // Bottom row
        g.putString(x, y + h - 1, String.valueOf(BL));
        if (w > 2)
            g.putString(x + 1, y + h - 1, String.valueOf(H).repeat(w - 2));
        g.putString(x + w - 1, y + h - 1, String.valueOf(BR));
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private static TextColor color(int r, int g, int b, TextColor fallback) {
        if (NO_COLOR) return TextColor.ANSI.DEFAULT;
        if (TRUECOLOR) return new TextColor.RGB(r, g, b);
        if (INDEXED_256) return new TextColor.Indexed(nearest256(r, g, b));
        return fallback;
    }

    /** Maps an RGB colour to the nearest xterm-256 index (cube or greyscale ramp). */
    private static int nearest256(int r, int g, int b) {
        int[] levels = {0, 95, 135, 175, 215, 255};
        int redIndex = nearestLevel(levels, r);
        int greenIndex = nearestLevel(levels, g);
        int blueIndex = nearestLevel(levels, b);
        int cubeIndex = 16 + 36 * redIndex + 6 * greenIndex + blueIndex;
        int cubeDistance = distance(r, g, b,
                levels[redIndex], levels[greenIndex], levels[blueIndex]);
        int average = (r + g + b) / 3;
        int grayIndex = Math.max(0, Math.min(23, (average - 3) / 10));
        int grayLevel = 8 + 10 * grayIndex;
        int grayDistance = distance(r, g, b, grayLevel, grayLevel, grayLevel);
        return grayDistance < cubeDistance ? 232 + grayIndex : cubeIndex;
    }

    private static int nearestLevel(int[] levels, int value) {
        int best = 0;
        for (int i = 1; i < levels.length; i++) {
            if (Math.abs(levels[i] - value) < Math.abs(levels[best] - value)) {
                best = i;
            }
        }
        return best;
    }

    private static int distance(int r, int g, int b, int rr, int gg, int bb) {
        int dr = r - rr;
        int dg = g - gg;
        int db = b - bb;
        return dr * dr + dg * dg + db * db;
    }

    private Theme() {}
}
