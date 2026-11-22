package com.ricky.thyme.feature.telemetry;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Records an event only when a named monitored state changes. An initial {@code "ok"} baseline is stored silently; initial abnormal states and subsequent transitions (including recoveries back to {@code "ok"}) are logged.
 */
public final class EventTransitionTracker {

    private final EventLogBuffer log;
    private final Map<String, String> states = new HashMap<>();

    public EventTransitionTracker(EventLogBuffer log) {
        this.log = log;
    }

    public synchronized void transition(String key, String state, EventLogBuffer.Severity severity, String message) {
        if ("unknown".equals(state))
            return;
        String previous = states.put(key, state);
        if (state.equals(previous))
            return;
        if (previous == null && "ok".equals(state))
            return;
        log.add(new EventLogBuffer.Event(Instant.now(), severity, message));
    }
}
