package com.ricky.thyme.feature.telemetry;

import java.util.Arrays;

/**
 * Fixed-capacity circular storage for primitive {@code double} samples.
 *
 * <p>Appending overwrites the oldest sample after the buffer is full.  All
 * operations are synchronized, so a collector may append while a renderer
 * reads a snapshot.  {@link #toArray()} returns a cached chronological
 * snapshot; the cache is replaced only after an append or clear.  Callers
 * must treat the returned array as read-only.</p>
 *
 * <p>{@link Double#NaN} is a missing sample, not zero.  The buffer stores it
 * unchanged; aggregation code decides whether missing samples are usable.</p>
 */
public final class RingBuffer {
    private final double[] buffer;
    private int head;
    private int size;
    private double[] snapshot = new double[0];
    private boolean snapshotValid = true;

    public RingBuffer(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive: " + capacity);
        }
        buffer = new double[capacity];
    }

    public synchronized void append(double value) {
        buffer[head] = value;
        head++;
        if (head == buffer.length) head = 0;
        if (size < buffer.length) size++;
        snapshotValid = false;
    }

    public synchronized int size() { return size; }

    public int capacity() { return buffer.length; }

    public synchronized boolean isEmpty() { return size == 0; }

    public synchronized void clear() {
        head = 0;
        size = 0;
        Arrays.fill(buffer, Double.NaN);
        snapshot = new double[0];
        snapshotValid = true;
    }

    /** Returns the chronological samples, oldest first; the returned array is read-only. */
    public synchronized double[] toArray() {
        if (snapshotValid) return snapshot;
        if (snapshot.length != size) snapshot = new double[size];
        int start = head - size;
        if (start < 0) start += buffer.length;
        for (int i = 0; i < size; i++) {
            snapshot[i] = buffer[start];
            if (++start == buffer.length) start = 0;
        }
        snapshotValid = true;
        return snapshot;
    }

    /** Copies the chronological samples into {@code destination} without allocating. */
    public synchronized int copyTo(double[] destination) {
        if (destination.length < size) {
            throw new IllegalArgumentException("Destination is smaller than the buffer");
        }
        int start = head - size;
        if (start < 0) start += buffer.length;
        for (int i = 0; i < size; i++) {
            destination[i] = buffer[start];
            if (++start == buffer.length) start = 0;
        }
        return size;
    }

    public synchronized boolean hasValue() { return size != 0; }

    /** Returns the newest sample, or {@code NaN} when empty. */
    public synchronized double latestValue() {
        if (size == 0) return Double.NaN;
        return buffer[head == 0 ? buffer.length - 1 : head - 1];
    }

    /** Returns the oldest sample, or {@code NaN} when empty. */
    public synchronized double oldestValue() {
        if (size == 0) return Double.NaN;
        int index = head - size;
        if (index < 0) index += buffer.length;
        return buffer[index];
    }

    /** Compatibility accessor; hot paths should use {@link #latestValue()}. */
    @Deprecated
    public synchronized Double latest() { return size == 0 ? null : latestValue(); }

    /** Compatibility accessor; hot paths should use {@link #oldestValue()}. */
    @Deprecated
    public synchronized Double oldest() { return size == 0 ? null : oldestValue(); }
}
