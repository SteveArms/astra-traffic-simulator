package com.astra.core;

import com.astra.graph.Intersection;
import com.astra.model.Vehicle;
import com.astra.model.VehicleFactory;
import com.astra.model.VehicleKind;
import com.astra.routing.RouteStrategies;
import com.astra.routing.RouteStrategy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/**
 * Genera vehículos entre intersecciones del borde del mapa, uno por intervalo de tiempo simulado. No tiene hilos
 * propios y usa una sola semilla: con la misma configuración, la secuencia es siempre la misma.
 *
 * @implNote Solo astra-engine lo usa, dentro del tick; antes de iniciar el motor puede usarse desde cualquier hilo.
 */
public final class VehicleSpawner {

    private static final double MILLIS_PER_SECOND = 1_000.0;

    private final WorldState world;
    private final VehicleFactory factory;
    private final Random random;
    private final double intervalSeconds;
    private final int maxCount;
    private final List<Intersection> border;
    private final Map<VehicleKind, Double> kindWeights;
    private final Map<RouteStrategy, Double> strategyWeights;
    private final Map<VehicleKind, Integer> spawnedByKind = new EnumMap<>(VehicleKind.class);

    private double accumulator;
    // Vehículo ya elegido que esperaba espacio en su primera vía.
    private Vehicle pending;

    public VehicleSpawner(WorldState world, SimConfig cfg) {
        this.world = Objects.requireNonNull(world, "world");
        this.factory = new VehicleFactory(cfg);
        this.random = new Random(cfg.spawnSeed());
        this.intervalSeconds = cfg.spawnIntervalMillis().toMillis() / MILLIS_PER_SECOND;
        this.maxCount = cfg.vehicleMaxCount();
        this.border = borderOf(world.graph().vertices());
        if (border.size() < 2) {
            throw new IllegalStateException("The map needs at least two border intersections to spawn vehicles");
        }
        this.kindWeights = cfg.spawnWeights();
        var strategies = new LinkedHashMap<RouteStrategy, Double>();
        cfg.routingWeights().forEach((name, weight) ->
                strategies.put(RouteStrategies.byName(name, world::currentOccupancy, cfg, random), weight));
        this.strategyWeights = Collections.unmodifiableMap(strategies);
        for (VehicleKind kind : VehicleKind.values()) {
            spawnedByKind.put(kind, 0);
        }
    }

    /** @implNote Called from astra-engine at the start of each tick. */
    public void update(double dtSeconds) {
        accumulator += dtSeconds;
        while (accumulator >= intervalSeconds) {
            accumulator -= intervalSeconds;
            trySpawn();
        }
    }

    public long spawned() {
        return world.spawned();
    }

    public long despawned() {
        return world.despawned();
    }

    public Map<VehicleKind, Integer> activeByKind() {
        return world.activeByKind();
    }

    /** Vehículos generados por este spawner, por tipo. */
    public Map<VehicleKind, Integer> spawnedByKind() {
        return Collections.unmodifiableMap(spawnedByKind);
    }

    /** Intersecciones de entrada y salida, ordenadas por id. */
    public List<Intersection> border() {
        return border;
    }

    private void trySpawn() {
        if (world.vehicleCount() >= maxCount) {
            return;
        }
        if (pending == null) {
            pending = create();
        }
        if (pending != null && hasRoom(pending)) {
            world.addVehicle(pending);
            spawnedByKind.merge(pending.kind(), 1, Integer::sum);
            pending = null;
        }
    }

    private Vehicle create() {
        VehicleKind kind = pick(kindWeights);
        RouteStrategy strategy = pick(strategyWeights);
        Intersection origin = border.get(random.nextInt(border.size()));
        Intersection destination = border.get(random.nextInt(border.size() - 1));
        if (destination.equals(origin)) {
            destination = border.get(border.size() - 1);
        }
        try {
            return factory.create(kind, strategy, origin, destination, world.graph());
        } catch (IllegalStateException e) {
            // Sin camino entre ese par: se elige otro en el próximo intervalo.
            return null;
        }
    }

    // El que nace queda en el centro de su origen: necesita sitio en su vía y en la zona.
    private boolean hasRoom(Vehicle vehicle) {
        return world.hasRoomAtStart(vehicle.currentRoad(), vehicle.length())
                && !world.zones().zoneOf(vehicle.origin()).isFull();
    }

    private <T> T pick(Map<T, Double> weights) {
        double total = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        double target = random.nextDouble() * total;
        T last = null;
        for (Map.Entry<T, Double> entry : weights.entrySet()) {
            if (entry.getValue() <= 0) {
                continue;
            }
            last = entry.getKey();
            target -= entry.getValue();
            if (target < 0) {
                return last;
            }
        }
        return last;
    }

    private static List<Intersection> borderOf(Iterable<Intersection> vertices) {
        List<Intersection> all = new ArrayList<>();
        vertices.forEach(all::add);
        double minX = all.stream().mapToDouble(Intersection::x).min().orElse(0);
        double maxX = all.stream().mapToDouble(Intersection::x).max().orElse(0);
        double minY = all.stream().mapToDouble(Intersection::y).min().orElse(0);
        double maxY = all.stream().mapToDouble(Intersection::y).max().orElse(0);
        return all.stream()
                .filter(i -> i.x() == minX || i.x() == maxX || i.y() == minY || i.y() == maxY)
                .sorted(Comparator.comparing(Intersection::id))
                .toList();
    }
}
