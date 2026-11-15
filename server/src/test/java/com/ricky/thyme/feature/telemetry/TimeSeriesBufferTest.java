package com.ricky.thyme.feature.telemetry;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class TimeSeriesBufferTest {
    @Test
    void missingSamplesDoNotBecomeZeroAndEmptyBucketStaysNaN() {
        TimeSeriesBuffer buffer = new TimeSeriesBuffer();
        for (int i = 0; i < 10; i++) buffer.append(Double.NaN);
        assertTrue(Double.isNaN(buffer.samples(TimeSeriesBuffer.Range.ONE_HOUR)[0]));
        assertTrue(Double.isNaN(buffer.average(TimeSeriesBuffer.Range.ONE_HOUR)));
        buffer.append(0.0);
        assertEquals(0.0, buffer.current());
    }

    @Test
    void aggregatesMeanAndPeakAtTenAndSixtySecondBoundaries() {
        TimeSeriesBuffer buffer = new TimeSeriesBuffer();
        for (int i = 1; i <= 60; i++) buffer.append(i);
        assertArrayEquals(new double[]{5.5, 15.5, 25.5, 35.5, 45.5, 55.5},
                buffer.samples(TimeSeriesBuffer.Range.ONE_HOUR), 0.000001);
        assertArrayEquals(new double[]{10, 20, 30, 40, 50, 60},
                buffer.peaks(TimeSeriesBuffer.Range.ONE_HOUR), 0.000001);
        assertEquals(30.5, buffer.average(TimeSeriesBuffer.Range.SIX_HOURS), 0.000001);
        assertEquals(60.0, buffer.peak(TimeSeriesBuffer.Range.SIX_HOURS));
    }

    @Test
    void effectiveDurationIncludesPartialBucketAndCoverageIsHonest() {
        TimeSeriesBuffer buffer = new TimeSeriesBuffer();
        for (int i = 0; i < 15; i++) buffer.append(1.0);
        assertEquals(Duration.ofSeconds(15), buffer.effectiveDuration(TimeSeriesBuffer.Range.ONE_HOUR));
        assertEquals("history covers 15s of 1h", buffer.coverageNote(TimeSeriesBuffer.Range.ONE_HOUR));
        assertEquals(Duration.ofSeconds(15), buffer.effectiveDuration(TimeSeriesBuffer.Range.SIX_HOURS));
        assertNull(new TimeSeriesBuffer().coverageNote(TimeSeriesBuffer.Range.FIFTEEN_MINUTES));
    }

    @Test
    void ageUsesLastValidSampleNotMissingTicks() {
        TimeSeriesBuffer buffer = new TimeSeriesBuffer();
        buffer.appendAt(4.0, 1_000_000_000L);
        buffer.appendAt(Double.NaN, 2_000_000_000L);
        assertEquals(Duration.ofSeconds(3), buffer.dataAgeAt(4_000_000_000L));
    }

    @Test
    void concurrentAppendAndReadDoNotCorruptSnapshots() throws Exception {
        TimeSeriesBuffer buffer = new TimeSeriesBuffer();
        CountDownLatch done = new CountDownLatch(1);
        Thread writer = new Thread(() -> {
            for (int i = 0; i < 10_000; i++) buffer.append(i);
            done.countDown();
        });
        writer.start();
        while (done.getCount() != 0) {
            double[] values = buffer.samples(TimeSeriesBuffer.Range.FIFTEEN_MINUTES);
            assertTrue(values.length <= TimeSeriesBuffer.CAP_1S);
        }
        assertTrue(done.await(1, TimeUnit.SECONDS));
    }
}
