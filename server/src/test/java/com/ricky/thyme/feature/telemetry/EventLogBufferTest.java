package com.ricky.thyme.feature.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class EventLogBufferTest {
    @Test
    void boundedLogOverwritesOldestEntry() {
        EventLogBuffer log = new EventLogBuffer(2);
        log.add(new EventLogBuffer.Event(Instant.ofEpochSecond(1), EventLogBuffer.Severity.GOOD, "a"));
        log.add(new EventLogBuffer.Event(Instant.ofEpochSecond(2), EventLogBuffer.Severity.WARM, "b"));
        log.add(new EventLogBuffer.Event(Instant.ofEpochSecond(3), EventLogBuffer.Severity.CRITICAL, "c"));
        assertEquals(2, log.size());
        assertEquals("b", log.snapshot().get(0).message());
        assertEquals("c", log.snapshot().get(1).message());
    }
}
