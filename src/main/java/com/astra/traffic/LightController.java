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
import java.util.function.DoubleSupplier;

/** Semáforos donde entran 3+ vías de los dos ejes (no en esquinas en L). Consultas desde cualquier hilo. */
public final class LightController {

    public static final String THREAD_PREFIX = "astra-lights-";

    private static final int POOL_SIZE = 2;
    private static final long STOP_TIMEOUT_MILLIS = 1_000;
    private static final int MIN_INCOMING_FOR_LIGHT = 3;

    private final Map<Intersection, TrafficLight> lights;
    private volatile ScheduledExecutorService executor;

    public LightController(RoadGraph graph, LightTimings timings) {
        this(graph, timings, () -> 1.0);
    }

    /**
     * @param speedFactor multiplicador de velocidad de la simulación; cada fase dura {@code duración / speedFactor}
     *                    y el valor se lee al programar cada transición
     */
    public LightController(RoadGraph graph, LightTimings timings, DoubleSupplier speedFactor) {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(timings, "timings");
        Objects.requireNonNull(speedFactor, "speedFactor");
        Map<Intersection, Set<Axis>> incomingAxes = new HashMap<>();
        Map<Intersection, Integer> incomingCount = new HashMap<>();
        for (Road road : graph.edges()) {
            incomingAxes.computeIfAbsent(road.to(), i -> EnumSet.noneOf(Axis.class)).add(Axis.of(road));
            incomingCount.merge(road.to(), 1, Integer::sum);
        }

        List<Double> columns = sortedUnique(graph.vertices().stream().map(Intersection::x).toList());
        List<Double> rows = sortedUnique(graph.vertices().stream().map(Intersection::y).toList());
        Map<Intersection, TrafficLight> created = new HashMap<>();
        for (Intersection intersection : graph.vertices()) {
            boolean bothAxes = incomingAxes.getOrDefault(intersection, Set.of()).size() == Axis.values().length;
            // En una esquina en L (2 vías) los flujos no se cruzan: basta la zona.
            if (bothAxes && incomingCount.getOrDefault(intersection, 0) >= MIN_INCOMING_FOR_LIGHT) {
                // Damero: vecinas con ejes opuestos.
                int parity = columns.indexOf(intersection.x()) + rows.indexOf(intersection.y());
                Axis first = parity % 2 == 0 ? Axis.NS : Axis.EW;
                created.put(intersection, new TrafficLight(intersection, timings, new GreenState(first), speedFactor));
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

    /**
     * Congela todos los semáforos guardando el tiempo que le falta a cada fase.
     *
     * @implNote Called from the thread that pauses the simulation (usually astra-engine), outside any other lock.
     */
    public void pause() {
        lights.values().forEach(TrafficLight::pause);
    }

    /** @implNote Called from the thread that resumes the simulation (usually astra-engine). */
    public void resume() {
        lights.values().forEach(TrafficLight::resume);
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

    /**
     * Sin semáforo en {@code incoming.to()} no hace nada.
     *
     * @implNote Called from astra-engine or astra-worker-N.
     */
    public void preempt(Road incoming, String vehicleId) {
        TrafficLight light = lights.get(incoming.to());
        if (light != null) {
            light.preempt(Axis.of(incoming), vehicleId);
        }
    }

    /** @implNote Called from astra-engine or astra-worker-N. */
    public void release(Intersection intersection, String vehicleId) {
        TrafficLight light = lights.get(intersection);
        if (light != null) {
            light.release(vehicleId);
        }
    }

    public Collection<TrafficLight> lights() {
        return lights.values();
    }

    private static List<Double> sortedUnique(List<Double> values) {
        return values.stream().distinct().sorted().toList();
    }
}
