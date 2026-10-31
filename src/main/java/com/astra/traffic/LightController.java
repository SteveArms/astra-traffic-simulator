package com.astra.traffic;

import com.astra.graph.Intersection;
import com.astra.graph.Road;
import com.astra.graph.RoadGraph;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Semáforos de la ciudad, solo donde entran vías de los dos ejes. Las consultas sirven desde cualquier hilo. */
public final class LightController {

    public static final String THREAD_PREFIX = "astra-lights-";

    private static final int POOL_SIZE = 2;
    private static final long STOP_TIMEOUT_MILLIS = 1_000;

    private final Map<Intersection, TrafficLight> lights;
    private volatile ScheduledExecutorService executor;

    public LightController(RoadGraph graph, LightTimings timings) {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(timings, "timings");
        Map<Intersection, Set<Axis>> incomingAxes = new HashMap<>();
        for (Road road : graph.edges()) {
            incomingAxes.computeIfAbsent(road.to(), i -> EnumSet.noneOf(Axis.class)).add(Axis.of(road));
        }

        List<Double> columns = sortedUnique(graph.vertices().stream().map(Intersection::x).toList());
        List<Double> rows = sortedUnique(graph.vertices().stream().map(Intersection::y).toList());
        Map<Intersection, TrafficLight> created = new HashMap<>();
        for (Intersection intersection : graph.vertices()) {
            if (incomingAxes.getOrDefault(intersection, Set.of()).size() == Axis.values().length) {
                // Damero: vecinas con ejes opuestos.
                int parity = columns.indexOf(intersection.x()) + rows.indexOf(intersection.y());
                Axis first = parity % 2 == 0 ? Axis.NS : Axis.EW;
                created.put(intersection, new TrafficLight(intersection, timings, new GreenState(first)));
            }
        }
        lights = Map.copyOf(created);
    }

    /** @implNote Called from the FX Application Thread. */
    public synchronized void start() {
        if (isRunning()) {
            throw new IllegalStateException("Light controller is already running");
        }
        var counter = new AtomicInteger();
        executor = Executors.newScheduledThreadPool(POOL_SIZE, runnable -> {
            var thread = new Thread(runnable, THREAD_PREFIX + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        for (TrafficLight light : lights.values()) {
            light.scheduleNext(executor);
        }
    }

    /** @implNote Called from the FX Application Thread. */
    public synchronized void stop() {
        ScheduledExecutorService current = executor;
        if (current == null) {
            return;
        }
        current.shutdownNow();
        try {
            current.awaitTermination(STOP_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        executor = null;
    }

    /** @implNote Safe to call from any thread. */
    public boolean isRunning() {
        ScheduledExecutorService current = executor;
        return current != null && !current.isShutdown();
    }

    public Optional<TrafficLight> lightAt(Intersection intersection) {
        return Optional.ofNullable(lights.get(intersection));
    }

    /** @implNote Called from astra-engine every tick. */
    public boolean canEnter(Road incoming) {
        TrafficLight light = lights.get(incoming.to());
        return light == null || light.canPass(Axis.of(incoming));
    }

    /** Sin semáforo devuelve GREEN. */
    public LightColor colorFor(Road incoming) {
        TrafficLight light = lights.get(incoming.to());
        return light == null ? LightColor.GREEN : light.colorFor(Axis.of(incoming));
    }

    public Collection<TrafficLight> lights() {
        return lights.values();
    }

    private static List<Double> sortedUnique(List<Double> values) {
        return values.stream().distinct().sorted().toList();
    }
}
