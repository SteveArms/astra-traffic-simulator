package com.astra.traffic;

import com.astra.graph.Intersection;
import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Semáforo de una intersección; su estado se lee desde cualquier hilo.
 *
 * @implNote {@link #transition()} corre en astra-lights-N, asíncrono respecto al motor.
 */
public final class TrafficLight {

    private final Intersection intersection;
    private final LightTimings timings;
    private final AtomicReference<LightState> state;
    private final AtomicLong transitions = new AtomicLong();
    private volatile ScheduledExecutorService executor;

    public TrafficLight(Intersection intersection, LightTimings timings, LightState initial) {
        this.intersection = Objects.requireNonNull(intersection, "intersection");
        this.timings = Objects.requireNonNull(timings, "timings");
        this.state = new AtomicReference<>(Objects.requireNonNull(initial, "initial"));
    }

    public LightState state() {
        return state.get();
    }

    public LightColor colorFor(Axis axis) {
        return state.get().colorFor(axis);
    }

    public boolean canPass(Axis axis) {
        return state.get().canPass(axis);
    }

    public long transitions() {
        return transitions.get();
    }

    public Intersection intersection() {
        return intersection;
    }

    /** Solo para tests, con el controlador detenido. */
    void forceState(LightState forced) {
        state.set(Objects.requireNonNull(forced, "forced"));
    }

    /** @implNote Called from LightController.start(), then from astra-lights-N. */
    public void scheduleNext(ScheduledExecutorService ex) {
        executor = Objects.requireNonNull(ex, "executor");
        long millis = state.get().duration(timings).toMillis();
        ex.schedule(this::transition, millis, TimeUnit.MILLISECONDS);
    }

    private void transition() {
        try {
            state.set(state.get().next());
            transitions.incrementAndGet();
        } catch (RuntimeException e) {
            // TODO: SimLogger (Día 6).
            System.err.println("[astra-lights] " + intersection.id() + " transition failed: " + e);
        }
        try {
            scheduleNext(executor);
        } catch (RejectedExecutionException e) {
            // El controlador se detuvo.
        }
    }
}
