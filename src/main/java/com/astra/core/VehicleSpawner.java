package com.astra.core;

import com.astra.graph.Intersection;
import com.astra.graph.NodeKind;
import com.astra.graph.Road;
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
import java.util.Optional;
import java.util.Random;

/**
 * Genera vehículos entre los extremos del mapa: {@code spawn.batchSize} por intervalo de tiempo simulado, sin superar
 * {@code vehicle.maxCount}. Si el mapa tiene nodos {@link NodeKind#GATEWAY}, los extremos son solo esos (entran y
 * salen de la ciudad por ellos); si no, las intersecciones del borde. No tiene hilos propios y usa una sola semilla:
 * con la misma configuración, la secuencia es siempre la misma.
 *
 * @implNote Solo astra-engine lo usa, dentro del tick; antes de iniciar el motor puede usarse desde cualquier hilo.
 */
public final class VehicleSpawner {

    private static final double MILLIS_PER_SECOND = 1_000.0;

    private final WorldState world;
    private final SimConfig cfg;
    private final VehicleFactory factory;
    private final Random random;
    private final double intervalSeconds;
    private final int batchSize;
    private final int maxCount;
    private final List<Intersection> border;
    private final boolean usesGateways;
    private final Map<VehicleKind, Double> kindWeights;
    private final Map<RouteStrategy, Double> strategyWeights;
    private final Map<VehicleKind, Integer> spawnedByKind = new EnumMap<>(VehicleKind.class);

    private double accumulator;
    private int pending;

    public VehicleSpawner(WorldState world, SimConfig cfg) {
        this.world = Objects.requireNonNull(world, "world");
        this.cfg = Objects.requireNonNull(cfg, "cfg");
        this.factory = new VehicleFactory(cfg);
        this.random = new Random(cfg.spawnSeed());
        this.intervalSeconds = cfg.spawnIntervalMillis().toMillis() / MILLIS_PER_SECOND;
        this.batchSize = cfg.spawnBatchSize();
        this.maxCount = cfg.vehicleMaxCount();
        this.border = endpointsOf(world.graph().vertices());
        this.usesGateways = border.stream().anyMatch(Intersection::isGateway);
        if (border.size() < 2) {
            throw new IllegalStateException("The map needs at least two gateways or border intersections");
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

    /** @implNote Called from astra-engine in the COMMIT phase of each tick. */
    public void update(double dtSeconds) {
        accumulator += dtSeconds;
        while (accumulator >= intervalSeconds) {
            accumulator -= intervalSeconds;
            pending = batchSize;
        }
        // Un intento por tick: los del grupo no nacen encima unos de otros y el que no cabe se reintenta.
        if (pending > 0 && trySpawn()) {
            pending--;
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

    /**
     * Genera ahora un vehículo pedido por el usuario. Un extremo {@code null} se sortea entre los extremos del mapa
     * (distinto del otro extremo).
     *
     * @return vacío si se alcanzó {@code vehicle.maxCount}, no hay ruta (incluye origen = destino) o no hay espacio
     * @implNote Called from astra-engine (SpawnVehicleCmd), or before the engine starts.
     */
    public Optional<Vehicle> spawnNow(VehicleKind kind, RouteStrategy strategy, Intersection origin,
            Intersection dest) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(strategy, "strategy");
        if (world.vehicleCount() >= maxCount) {
            return Optional.empty();
        }
        Intersection from = origin != null ? origin : randomBorderExcept(dest);
        Intersection to = dest != null ? dest : randomBorderExcept(from);
        return place(kind, strategy, from, to);
    }

    /**
     * Estrategia nueva con los parámetros y la ocupación de este mundo.
     *
     * @throws IllegalArgumentException si el nombre no existe
     */
    public RouteStrategy strategyNamed(String name) {
        return RouteStrategies.byName(name, world::currentOccupancy, cfg, random);
    }

    /** Nodos de entrada y salida, ordenados por id: los gateways si el mapa los tiene, si no el borde. */
    public List<Intersection> border() {
        return border;
    }

    /** {@code true} si los vehículos entran y salen solo por nodos GATEWAY. */
    public boolean usesGateways() {
        return usesGateways;
    }

    // Si el par elegido no tiene camino o espacio, se descarta y el próximo tick sortea otro: así un origen
    // congestionado no bloquea a los demás. El vehículo solo se crea si entra, y no gasta un id.
    private boolean trySpawn() {
        if (world.vehicleCount() >= maxCount) {
            return false;
        }
        VehicleKind kind = pick(kindWeights);
        RouteStrategy strategy = pick(strategyWeights);
        Intersection origin = border.get(random.nextInt(border.size()));
        Intersection destination = border.get(random.nextInt(border.size() - 1));
        if (destination.equals(origin)) {
            destination = border.get(border.size() - 1);
        }
        return place(kind, strategy, origin, destination).isPresent();
    }

    private Optional<Vehicle> place(VehicleKind kind, RouteStrategy strategy, Intersection origin,
            Intersection destination) {
        if (origin.equals(destination)) {
            return Optional.empty();
        }
        List<Road> route = strategy.route(world.graph(), origin, destination);
        if (route.isEmpty() || !hasRoom(route.get(0), cfg.specFor(kind).length())) {
            return Optional.empty();
        }
        Vehicle vehicle = factory.create(kind, strategy, route);
        world.addVehicle(vehicle);
        spawnedByKind.merge(kind, 1, Integer::sum);
        return Optional.of(vehicle);
    }

    private Intersection randomBorderExcept(Intersection excluded) {
        List<Intersection> candidates =
                excluded == null ? border : border.stream().filter(i -> !i.equals(excluded)).toList();
        return candidates.get(random.nextInt(candidates.size()));
    }

    // El que nace queda en el centro de su origen: necesita sitio en su vía y en la zona.
    private boolean hasRoom(Road first, double length) {
        return world.hasRoomAtStart(first, length) && !world.zones().zoneOf(first.from()).isFull();
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

    private static List<Intersection> endpointsOf(Iterable<Intersection> vertices) {
        List<Intersection> all = new ArrayList<>();
        vertices.forEach(all::add);
        List<Intersection> gateways = all.stream()
                .filter(Intersection::isGateway)
                .sorted(Comparator.comparing(Intersection::id))
                .toList();
        if (!gateways.isEmpty()) {
            return gateways;
        }
        // Mapa sin gateways (city-3x3.json): las intersecciones del rectángulo exterior.
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
