package com.ricky.thyme.feature.telemetry;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.concurrent.CountDownLatch;

class RingBufferTest {

    @Test
    void rejectsNonPositiveCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new RingBuffer(0));
        assertThrows(IllegalArgumentException.class, () -> new RingBuffer(-5));
    }

    @Test
    void initialEmptyState() {
        RingBuffer rb = new RingBuffer(5);
        assertEquals(5, rb.capacity());
        assertEquals(0, rb.size());
        assertTrue(rb.isEmpty());
        assertTrue(Double.isNaN(rb.latestValue()));
        assertTrue(Double.isNaN(rb.oldestValue()));
        assertArrayEquals(new double[0], rb.toArray());
    }

    @Test
    void appendsUnderCapacity() {
        RingBuffer rb = new RingBuffer(5);
        rb.append(10.0);
        rb.append(20.0);
        assertEquals(2, rb.size());
        assertFalse(rb.isEmpty());
        assertEquals(20.0, rb.latestValue());
        assertEquals(10.0, rb.oldestValue());
        assertArrayEquals(new double[]{10.0, 20.0}, rb.toArray());
    }

    @Test
    void overwritesOldestWhenCapacityExceeded() {
        RingBuffer rb = new RingBuffer(3);
        rb.append(1.0);
        rb.append(2.0);
        rb.append(3.0);
        assertEquals(3, rb.size());
        assertArrayEquals(new double[]{1.0, 2.0, 3.0}, rb.toArray());

        rb.append(4.0);
        assertEquals(3, rb.size());
        assertEquals(4.0, rb.latestValue());
        assertEquals(2.0, rb.oldestValue());
        assertArrayEquals(new double[]{2.0, 3.0, 4.0}, rb.toArray());

        rb.append(5.0);
        assertEquals(3, rb.size());
        assertEquals(5.0, rb.latestValue());
        assertEquals(3.0, rb.oldestValue());
        assertArrayEquals(new double[]{3.0, 4.0, 5.0}, rb.toArray());
    }

    @Test
    void clearsBuffer() {
        RingBuffer rb = new RingBuffer(3);
        rb.append(1.0);
        rb.append(2.0);
        rb.clear();
        assertEquals(0, rb.size());
        assertTrue(rb.isEmpty());
        assertTrue(Double.isNaN(rb.latestValue()));
        assertArrayEquals(new double[0], rb.toArray());
    }

    @Test
    void concurrentAppendAndSnapshotDoNotCorruptState() throws InterruptedException {
        RingBuffer rb = new RingBuffer(32);
        CountDownLatch start = new CountDownLatch(1);
        Thread writer = new Thread(() -> {
            try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            for (int i = 0; i < 10_000; i++) rb.append(i);
        });
        Thread reader = new Thread(() -> {
            try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            for (int i = 0; i < 10_000; i++) assertTrue(rb.toArray().length <= rb.capacity());
        });
        writer.start(); reader.start(); start.countDown();
        writer.join(); reader.join();
        assertEquals(rb.capacity(), rb.size());
    }
}
