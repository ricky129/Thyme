package com.ricky.thyme.feature.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class EventTransitionTrackerTest {
    @Test
    void recordsOnlyStateTransitions() {
        EventLogBuffer log = new EventLogBuffer();
        EventTransitionTracker tracker = new EventTransitionTracker(log);
        tracker.transition("service:sshd", "ok", EventLogBuffer.Severity.GOOD, "ok");
        tracker.transition("service:sshd", "ok", EventLogBuffer.Severity.GOOD, "ok again");
        tracker.transition("service:sshd", "failed", EventLogBuffer.Severity.CRITICAL, "failed");
        tracker.transition("service:sshd", "failed", EventLogBuffer.Severity.CRITICAL, "failed again");
        assertEquals(2, log.size());
        assertEquals("failed", log.snapshot().get(1).message());
    }
}
