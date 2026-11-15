package com.ricky.thyme.feature.telemetry;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class MetricSchedulerTest {
    @Test
    void oneSecondTickFeedsNaNBetweenSeparateProviderCadences() {
        TimeSeriesBuffer buffer = new TimeSeriesBuffer();
        AtomicInteger calls = new AtomicInteger();
        try (MetricScheduler scheduler = new MetricScheduler()) {
            scheduler.register(buffer, Duration.ofSeconds(5), () -> calls.incrementAndGet());
            for (int i = 0; i < 10; i++) scheduler.tickNow();
        }
        assertEquals(2, calls.get());
        assertArrayEquals(new double[]{Double.NaN, Double.NaN, Double.NaN, Double.NaN, 1,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN, 2},
                buffer.samples(TimeSeriesBuffer.Range.FIFTEEN_MINUTES));
    }
}
