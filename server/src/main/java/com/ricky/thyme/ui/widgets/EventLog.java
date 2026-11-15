package com.ricky.thyme.ui.widgets;

import com.googlecode.lanterna.graphics.TextGraphics;
import com.ricky.thyme.ui.Theme;
import java.util.List;

/**
 * A bounded in-memory event log widget.
 *
 * <p>Renders a list of event entries, each on one row:
 * <pre>
 *   time ● event title                    ›
 * </pre>
 *
 * <p>Entries are drawn from the most-recent downward; older entries scroll
 * off the top of the visible area.
 *
 * <p>This widget is <em>render-only</em> and holds no domain state.
 * Event storage is handled separately (see the ring buffer in
 * {@code feature/telemetry}).
 */
public final class EventLog {

    private EventLog() {}

    /**
     * An immutable event entry for display.
     *
     * @param time     formatted timestamp string (e.g. {@code "14:03:21"})
     * @param dotColor colour for the severity dot ({@link Theme#GOOD},
     *                 {@link Theme#WARM} or {@link Theme#CRITICAL})
     * @param message  event description text
     */
    public record Entry(
            String time,
            com.googlecode.lanterna.TextColor dotColor,
            String message) {}

    /**
     * Draws the event log inside the given rectangle.
     *
     * @param g       graphics context
     * @param x       left column
     * @param y       top row
     * @param w       width in columns (must be ≥ 12)
     * @param h       height in rows (determines how many entries are visible)
     * @param title   panel title (e.g. {@code "Events · 12"})
     * @param entries list of entries, most-recent first
     */
    public static void draw(
            TextGraphics g,
            int x, int y, int w, int h,
            String title,
            List<Entry> entries) {

        if (w < 8 || h < 2) return;

        // Outer border with title
        Panel.draw(g, x, y, w, h, title);

        int ix = Panel.interiorX(x);
        int iy = Panel.interiorY(y);
        int iw = Panel.interiorW(w);
        int ih = Panel.interiorH(h);

        if (iw <= 0 || ih <= 0 || entries == null || entries.isEmpty()) {
            if (ih > 0) Theme.put(g, ix, iy, iw, "No events", Theme.INK_MUTED);
            return;
        }

        // Show the most-recent entries that fit in 'ih' rows
        int start = Math.max(0, entries.size() - ih);
        int row = 0;
        for (int i = start; i < entries.size() && row < ih; i++, row++) {
            Entry e = entries.get(i);
            int col = ix;

            // Time
            String time = e.time() != null ? e.time() : "";
            Theme.put(g, col, iy + row, Math.min(time.length(), iw), time, Theme.INK_MUTED);
            col += time.length() + 1;
            if (col >= ix + iw) continue;

            // Dot
            g.setForegroundColor(e.dotColor() != null ? e.dotColor() : Theme.INK_MUTED);
            g.putString(col, iy + row, StatusPill.DOT);
            col += 2;
            if (col >= ix + iw) continue;

            // Message (with trailing › if it fits)
            int msgW = ix + iw - col;
            String msg = e.message() != null ? e.message() : "";
            Theme.put(g, col, iy + row, msgW, msg, Theme.INK_SECONDARY);
        }
    }
}
