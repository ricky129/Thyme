package com.ricky.thyme.feature.telemetry;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Bounded event history; the oldest event is overwritten at capacity. */
public final class EventLogBuffer {
    public static final int DEFAULT_CAPACITY = 200;

    public enum Severity { GOOD, WARM, CRITICAL }

    public record Event(Instant time, Severity severity, String message) {
        public Event {
            if (time == null || severity == null || message == null) throw new NullPointerException();
        }
    }

    private final Event[] events;
    private int head;
    private int size;

    public EventLogBuffer() { this(DEFAULT_CAPACITY); }

    public EventLogBuffer(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("Capacity must be positive");
        events = new Event[capacity];
    }

    public synchronized void add(Event event) {
        events[head] = event;
        head = (head + 1) % events.length;
        if (size < events.length) size++;
    }

    public synchronized int size() { return size; }
    public int capacity() { return events.length; }

    /** Returns oldest-first entries. Allocation happens only when the UI asks to render the log. */
    public synchronized List<Event> snapshot() {
        List<Event> result = new ArrayList<>(size);
        int start = head - size;
        if (start < 0) start += events.length;
        for (int i = 0; i < size; i++) result.add(events[(start + i) % events.length]);
        return List.copyOf(result);
    }
}
