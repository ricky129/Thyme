package com.ricky.thyme.feature.telemetry;

import java.time.Duration;
import java.time.Instant;

/**
 * A one-second ticked, multi-resolution telemetry history.
 *
 * <p>The owner must call {@link #append(double)} once per clock tick, even
 * when no fresh provider value exists.  In that case it passes {@code NaN}.
 * NaN is missing data: it is excluded from averages and a bucket containing
 * no valid samples remains NaN (never {@code 0.0}).  Widgets should render
 * NaN samples as a gap in the chart, not as a zero.</p>
 *
 * <p>Ten-second and sixty-second tiers contain both the mean and the maximum
 * of each completed wall-clock bucket.  The tick clock, rather than append
 * count from a provider, determines bucket boundaries.</p>
 */
public final class TimeSeriesBuffer {
    public static final int CAP_1S = 900;
    public static final int CAP_10S = 360;
    public static final int CAP_60S = 360;
    private static final int SECONDS_10 = 10;
    private static final int SECONDS_60 = 60;

    public enum Range {
        FIFTEEN_MINUTES("15M", Duration.ofMinutes(15)),
        ONE_HOUR("1H", Duration.ofHours(1)),
        SIX_HOURS("6H", Duration.ofHours(6));

        private final String label;
        private final Duration duration;
        Range(String label, Duration duration) { this.label = label; this.duration = duration; }
        public String label() { return label; }
        public Duration duration() { return duration; }
        public Range next() { return switch (this) {
            case FIFTEEN_MINUTES -> ONE_HOUR; case ONE_HOUR -> SIX_HOURS; case SIX_HOURS -> FIFTEEN_MINUTES;
        }; }
        public Range previous() { return switch (this) {
            case FIFTEEN_MINUTES -> SIX_HOURS; case ONE_HOUR -> FIFTEEN_MINUTES; case SIX_HOURS -> ONE_HOUR;
        }; }
    }

    private final RingBuffer tier1s = new RingBuffer(CAP_1S);
    private final RingBuffer tier10s = new RingBuffer(CAP_10S);
    private final RingBuffer tier60s = new RingBuffer(CAP_60S);
    private final RingBuffer peak10s = new RingBuffer(CAP_10S);
    private final RingBuffer peak60s = new RingBuffer(CAP_60S);
    private final Duration tickInterval;

    private int ticksIn10;
    private int ticksIn60;
    private int valid10;
    private int valid60;
    private double sum10;
    private double sum60;
    private double max10 = Double.NaN;
    private double max60 = Double.NaN;
    private int totalTicks;
    private long lastValidNanos = Long.MIN_VALUE;

    public TimeSeriesBuffer() { this(Duration.ofSeconds(1)); }

    public TimeSeriesBuffer(Duration tickInterval) {
        if (tickInterval == null || tickInterval.isZero() || tickInterval.isNegative()) {
            throw new IllegalArgumentException("tickInterval must be positive");
        }
        this.tickInterval = tickInterval;
    }

    /** Adds one one-second tick. Use NaN when the provider did not produce a new value. */
    public void append(double value) { appendAt(value, System.nanoTime()); }

    /** Alias used by scheduler adapters: one call always advances the wall-clock tick. */
    public void tick(double value) { append(value); }

    /** Adds one tick with an explicit monotonic timestamp, useful to deterministic callers/tests. */
    public synchronized void appendAt(double value, long timestampNanos) {
        tier1s.append(value);
        totalTicks++;
        if (!Double.isNaN(value)) lastValidNanos = timestampNanos;

        ticksIn10++;
        ticksIn60++;
        if (!Double.isNaN(value)) {
            sum10 += value; valid10++; max10 = Double.isNaN(max10) ? value : Math.max(max10, value);
            sum60 += value; valid60++; max60 = Double.isNaN(max60) ? value : Math.max(max60, value);
        }
        if (ticksIn10 == SECONDS_10) {
            tier10s.append(valid10 == 0 ? Double.NaN : sum10 / valid10);
            peak10s.append(max10);
            ticksIn10 = 0;
            valid10 = 0;
            sum10 = 0;
            max10 = Double.NaN;
        }
        if (ticksIn60 == SECONDS_60) {
            tier60s.append(valid60 == 0 ? Double.NaN : sum60 / valid60);
            peak60s.append(max60);
            ticksIn60 = 0;
            valid60 = 0;
            sum60 = 0;
            max60 = Double.NaN;
        }
    }

    public synchronized double[] samples(Range range) { return ring(range).toArray(); }

    /** Returns per-bucket maxima for slow ranges; 1s returns the raw values as its peak. */
    public synchronized double[] peaks(Range range) { return peakRing(range).toArray(); }

    public synchronized int total1sSamples() { return totalTicks; }

    /** Returns the current (newest) tick, which can be NaN when the newest tick is missing. */
    public synchronized double current() { return tier1s.latestValue(); }

    public synchronized double current(Range range) {
        return ring(range).latestValue();
    }

    /** Mean of valid samples in the selected range, or NaN if there are none. */
    public synchronized double average(Range range) { return aggregate(ring(range).toArray(), false); }

    /** Maximum of valid samples/buckets in the selected range, or NaN if there are none. */
    public synchronized double peak(Range range) { return aggregate(peakRing(range).toArray(), true); }

    private static double aggregate(double[] values, boolean maximum) {
        double result = maximum ? Double.NaN : 0.0;
        int count = 0;
        for (double value : values) if (!Double.isNaN(value)) {
            result = maximum ? (Double.isNaN(result) ? value : Math.max(result, value)) : result + value;
            count++;
        }
        return count == 0 ? Double.NaN : maximum ? result : result / count;
    }

    /** Elapsed tick coverage retained by this range, including an incomplete current bucket. */
    public synchronized Duration effectiveDuration(Range range) {
        int available = Math.min(totalTicks, capacitySeconds(range));
        return tickInterval.multipliedBy(available);
    }

    /** Honest coverage note; null means empty or complete coverage. */
    public synchronized String coverageNote(Range range) {
        Duration effective = effectiveDuration(range);
        if (effective.isZero() || effective.compareTo(range.duration()) >= 0) return null;
        long seconds = effective.toSeconds();
        String covered = seconds < 60 ? seconds + "s" : (seconds / 60) + "m";
        String target = range == Range.FIFTEEN_MINUTES ? "15m" : range == Range.ONE_HOUR ? "1h" : "6h";
        return "history covers " + covered + " of " + target;
    }

    /** Age at the supplied monotonic timestamp; NaN/empty history has no age and returns null. */
    public synchronized Duration dataAgeAt(long nowNanos) {
        if (lastValidNanos == Long.MIN_VALUE) return null;
        return Duration.ofNanos(Math.max(0L, nowNanos - lastValidNanos));
    }

    /** Age based on the system monotonic clock. */
    public synchronized Duration latestAge() { return dataAgeAt(System.nanoTime()); }

    public Duration tickInterval() { return tickInterval; }

    /** Wall-clock age helper for callers that store an {@link Instant} timestamp separately. */
    public synchronized Duration dataAge(Instant now, Instant lastValid) {
        return lastValid == null ? null : Duration.between(lastValid, now).isNegative()
                ? Duration.ZERO : Duration.between(lastValid, now);
    }

    private RingBuffer ring(Range range) { return switch (range) {
        case FIFTEEN_MINUTES -> tier1s; case ONE_HOUR -> tier10s; case SIX_HOURS -> tier60s;
    }; }
    private RingBuffer peakRing(Range range) { return switch (range) {
        case FIFTEEN_MINUTES -> tier1s; case ONE_HOUR -> peak10s; case SIX_HOURS -> peak60s;
    }; }
    private static int capacitySeconds(Range range) { return switch (range) {
        case FIFTEEN_MINUTES -> CAP_1S; case ONE_HOUR -> CAP_10S * SECONDS_10; case SIX_HOURS -> CAP_60S * SECONDS_60;
    }; }
}
