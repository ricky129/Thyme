package com.ricky.thyme.feature.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class EventTransitionTrackerTest {
    @Test
    void suppressesInitialOkBaselineAndRecordsSubsequentTransitions() {
        EventLogBuffer log = new EventLogBuffer();
        EventTransitionTracker tracker = new EventTransitionTracker(log);
        tracker.transition("service:sshd", "unknown", EventLogBuffer.Severity.GOOD, "ignored");
        tracker.transition("service:sshd", "ok", EventLogBuffer.Severity.GOOD, "ok");
        tracker.transition("service:sshd", "ok", EventLogBuffer.Severity.GOOD, "ok again");
        assertEquals(0, log.size());

        tracker.transition("service:sshd", "failed", EventLogBuffer.Severity.CRITICAL, "failed");
        tracker.transition("service:sshd", "failed", EventLogBuffer.Severity.CRITICAL, "failed again");
        assertEquals(1, log.size());
        assertEquals("failed", log.snapshot().get(0).message());
        assertEquals(EventLogBuffer.Severity.CRITICAL, log.snapshot().get(0).severity());

        tracker.transition("service:sshd", "ok", EventLogBuffer.Severity.GOOD, "recovered");
        tracker.transition("service:sshd", "ok", EventLogBuffer.Severity.GOOD, "recovered again");
        assertEquals(2, log.size());
        assertEquals("recovered", log.snapshot().get(1).message());
        assertEquals(EventLogBuffer.Severity.GOOD, log.snapshot().get(1).severity());
    }

    @Test
    void recordsInitialAbnormalStateImmediately() {
        EventLogBuffer log = new EventLogBuffer();
        EventTransitionTracker tracker = new EventTransitionTracker(log);
        tracker.transition("service:sshd", "failed", EventLogBuffer.Severity.CRITICAL, "failed");
        assertEquals(1, log.size());
        assertEquals("failed", log.snapshot().get(0).message());
    }

    @Test
    void preservesPriorStateAcrossUnknownSamples() {
        EventLogBuffer log = new EventLogBuffer();
        EventTransitionTracker tracker = new EventTransitionTracker(log);
        tracker.transition("temp:gpu0", "ok", EventLogBuffer.Severity.GOOD, "ok");
        tracker.transition("temp:gpu0", "warning", EventLogBuffer.Severity.WARM, "warn");
        tracker.transition("temp:gpu0", "unknown", EventLogBuffer.Severity.GOOD, "unknown");
        tracker.transition("temp:gpu0", "warning", EventLogBuffer.Severity.WARM, "warn again");
        assertEquals(1, log.size());
        assertEquals("warn", log.snapshot().get(0).message());
    }
}
