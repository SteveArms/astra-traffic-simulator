package com.astra.core;

import com.astra.model.VehicleKind;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Métricas de la simulación en tiempo simulado. Por tick: {@link #advance}, luego {@link #recordArrival} por cada
 * vehículo que llegó y al final {@link #sample}.
 *
 * @implNote Solo astra-engine lo usa, en la fase C; la UI lee {@link #toView()} a través del snapshot.
 */
public final class SimStats {

    private static final double SECONDS_PER_MINUTE = 60.0;
    private static final double MILLIS_PER_SECOND = 1_000.0;
    // Absorbe el error de sumar dt (0.05 * 20 = 0.9999999…) al decidir si toca muestrear.
    private static final double EPSILON = 1e-9;

    private final double windowSeconds;
    private final double sampleSeconds;
    private final int capacity;
    private final Deque<Double> arrivalTimes = new ArrayDeque<>();
    private final Deque<StatsPoint> series = new ArrayDeque<>();
    private final Map<VehicleKind, Long> arrivedByKind = new EnumMap<>(VehicleKind.class);
    private final Map<VehicleKind, Double> waitByKind = new EnumMap<>(VehicleKind.class);

    private double now;
    private double nextSampleAt;
    private long arrived;
    private double tripSum;
    private double waitSum;
    private int currentQueue;
    private int maxQueue;

    public SimStats(SimConfig cfg) {
        this(cfg.statsThroughputWindow(), cfg.statsSampleInterval(), cfg.statsSeriesCapacity());
    }

    public SimStats(Duration throughputWindow, Duration sampleInterval, int seriesCapacity) {
        Objects.requireNonNull(throughputWindow, "throughputWindow");
        Objects.requireNonNull(sampleInterval, "sampleInterval");
        if (throughputWindow.isNegative() || throughputWindow.isZero() || sampleInterval.isNegative()
                || sampleInterval.isZero() || seriesCapacity < 1) {
            throw new IllegalArgumentException("window and interval must be > 0 and capacity >= 1");
        }
        this.windowSeconds = throughputWindow.toMillis() / MILLIS_PER_SECOND;
        this.sampleSeconds = sampleInterval.toMillis() / MILLIS_PER_SECOND;
        this.capacity = seriesCapacity;
        this.nextSampleAt = sampleSeconds;
    }

    public void advance(double dtSeconds) {
        now += dtSeconds;
    }

    public void recordArrival(VehicleKind kind, double tripSeconds, double waitSeconds) {
        Objects.requireNonNull(kind, "kind");
        arrived++;
        tripSum += tripSeconds;
        waitSum += waitSeconds;
        arrivedByKind.merge(kind, 1L, Long::sum);
        waitByKind.merge(kind, waitSeconds, Double::sum);
        arrivalTimes.addLast(now);
    }

    /** Cierra el tick: actualiza colas, descarta llegadas fuera de la ventana y agrega los puntos que tocan. */
    public void sample(int activeVehicles, int totalQueue) {
        currentQueue = totalQueue;
        maxQueue = Math.max(maxQueue, totalQueue);
        while (!arrivalTimes.isEmpty() && now - arrivalTimes.peekFirst() > windowSeconds + EPSILON) {
            arrivalTimes.pollFirst();
        }
        while (now + EPSILON >= nextSampleAt) {
            if (series.size() == capacity) {
                series.pollFirst();
            }
            series.addLast(new StatsPoint(now, activeVehicles, avgWait(), throughputPerMin()));
            nextSampleAt += sampleSeconds;
        }
    }

    public double simTimeSeconds() {
        return now;
    }

    public StatsView toView() {
        Map<VehicleKind, Double> byKind = new EnumMap<>(VehicleKind.class);
        arrivedByKind.forEach((kind, count) -> byKind.put(kind, waitByKind.get(kind) / count));
        return new StatsView(arrived == 0 ? 0 : tripSum / arrived, avgWait(), byKind, throughputPerMin(), arrived,
                currentQueue, maxQueue, series.stream().toList());
    }

    private double avgWait() {
        return arrived == 0 ? 0 : waitSum / arrived;
    }

    private double throughputPerMin() {
        return arrivalTimes.size() * SECONDS_PER_MINUTE / windowSeconds;
    }
}
