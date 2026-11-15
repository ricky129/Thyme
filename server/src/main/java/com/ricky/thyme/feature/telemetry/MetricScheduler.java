package com.ricky.thyme.feature.telemetry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.DoubleSupplier;

/**
 * One monotonic one-second clock shared by metric series.
 * Every registered buffer receives exactly one tick per clock invocation;
 * between a series' configured provider cadences it receives NaN.
 */
public final class MetricScheduler implements AutoCloseable {
    private static final long TICK_NANOS = Duration.ofSeconds(1).toNanos();
    private final ScheduledExecutorService executor;
    private final ArrayList<Series> series = new ArrayList<>();
    private ScheduledFuture<?> task;
    private long tick;

    public MetricScheduler() {
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "thyme-metric-ticker");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Registers a series. Cadence must be a positive whole number of seconds. */
    public synchronized void register(TimeSeriesBuffer buffer, Duration cadence, DoubleSupplier provider) {
        long seconds = cadence.toSeconds();
        if (seconds <= 0 || cadence.toNanos() != seconds * TICK_NANOS) {
            throw new IllegalArgumentException("Cadence must be a positive whole number of seconds");
        }
        series.add(new Series(buffer, seconds, provider));
    }

    /** Starts the shared clock once; the first tick occurs after one second. */
    public synchronized void start() {
        if (task == null) task = executor.scheduleAtFixedRate(this::tick, 1, 1, TimeUnit.SECONDS);
    }

    /** Performs one tick synchronously; useful for deterministic tests and manual clocks. */
    public synchronized void tickNow() {
        tick();
    }

    private synchronized void tick() {
        long now = System.nanoTime();
        tick++;
        for (int i = 0; i < series.size(); i++) {
            Series item = series.get(i);
            double value = tick % item.cadenceSeconds == 0 ? item.provider.getAsDouble() : Double.NaN;
            item.buffer.appendAt(value, now);
        }
    }

    @Override public synchronized void close() {
        if (task != null) task.cancel(false);
        executor.shutdownNow();
    }

    private record Series(TimeSeriesBuffer buffer, long cadenceSeconds, DoubleSupplier provider) {}
}
