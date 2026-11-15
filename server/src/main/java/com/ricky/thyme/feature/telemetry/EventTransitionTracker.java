package com.ricky.thyme.feature.telemetry;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/** Records an event only when a named monitored state changes. */
public final class EventTransitionTracker {
    private final EventLogBuffer log;
    private final Map<String, String> states = new HashMap<>();

    public EventTransitionTracker(EventLogBuffer log) { this.log = log; }

    public synchronized void transition(String key, String state, EventLogBuffer.Severity severity, String message) {
        String previous = states.put(key, state);
        if (!state.equals(previous) && !"unknown".equals(state))
            log.add(new EventLogBuffer.Event(Instant.now(), severity, message));
    }
}
