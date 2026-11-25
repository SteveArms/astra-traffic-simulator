package com.astra.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.astra.graph.Intersection;
import com.astra.graph.Road;
import com.astra.graph.RoadGraph;
import com.astra.model.Vehicle;
import com.astra.model.VehicleKind;
import com.astra.routing.RouteStrategies;
import com.astra.routing.RouteStrategy;
import com.astra.traffic.LightController;
import com.astra.traffic.TestLights;
import com.astra.traffic.TrafficLight;
import com.astra.util.MapLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * El motor avanza con step() y los semáforos se avanzan a mano por ticks. La reproducibilidad se comprueba en modo
 * secuencial: en paralelo los workers compiten de verdad por las zonas y el orden de llegada varía.
 */
@Timeout(60)
class VehicleSpawnerTest {

    // Cada fase dura 100 ticks = 5 s simulados.
    private static final int TICKS_PER_LIGHT_PHASE = 100;
    private static final double PROPORTION_TOLERANCE = 0.1;

    private record Counts(long spawned, long despawned, Map<VehicleKind, Integer> active,
            Map<VehicleKind, Integer> spawnedByKind) {
    }

    private static final class Run {
        private final WorldState world;
        private final VehicleSpawner spawner;
        private final SimulationEngine engine;
        private final LightController lights;

        Run(SimConfig cfg) {
            this(cfg, "maps/city-3x3.json");
        }

        Run(SimConfig cfg, String mapFile) {
            RoadGraph graph = MapLoader.load(mapFile).graph();
            lights = new LightController(graph, cfg);
            world = new WorldState(graph, cfg, lights, new IntersectionZones(graph, cfg));
            spawner = new VehicleSpawner(world, cfg);
            engine = new SimulationEngine(cfg, world, spawner);
        }

        Counts run(int ticks, Consumer<Run> afterTick) {
            try {
                for (int i = 1; i <= ticks; i++) {
                    engine.step();
                    if (i % TICKS_PER_LIGHT_PHASE == 0) {
                        for (TrafficLight light : lights.lights()) {
                            TestLights.force(light, light.state().next());
                        }
                    }
                    afterTick.accept(this);
                }
            } finally {
                engine.stop();
            }
            return new Counts(spawner.spawned(), spawner.despawned(), Map.copyOf(spawner.activeByKind()),
                    Map.copyOf(spawner.spawnedByKind()));
        }

        Counts run(int ticks) {
            return run(ticks, r -> { });
        }
    }

    @Test
    void sameSeedGivesSameCounts() {
        var props = new Properties();
        props.setProperty(SimConfig.ENGINE_PARALLEL, "false");
        Counts first = new Run(SimConfig.from(props)).run(3_000);
        Counts second = new Run(SimConfig.from(props)).run(3_000);

        assertTrue(first.spawned() > 0);
        assertEquals(first, second);
    }

    @Test
    void neverExceedsMaxCount() {
        var props = new Properties();
        props.setProperty(SimConfig.VEHICLE_MAX_COUNT, "12");
        props.setProperty(SimConfig.SPAWN_INTERVAL_MILLIS, "100");
        var run = new Run(SimConfig.from(props));
        int[] peak = {0};

        run.run(3_000, r -> {
            int count = r.world.vehicleCount();
            assertTrue(count <= 12, "vehicles " + count);
            peak[0] = Math.max(peak[0], count);
        });

        assertEquals(12, peak[0], "max count never reached");
    }

    @Test
    void spawnsOneBatchPerInterval() {
        var props = new Properties();
        props.setProperty(SimConfig.ENGINE_PARALLEL, "false");
        props.setProperty(SimConfig.SPAWN_INTERVAL_MILLIS, "10000");
        props.setProperty(SimConfig.SPAWN_BATCH_SIZE, "3");
        var run = new Run(SimConfig.from(props));
        int ticksPerInterval = 200;

        // 1100 ticks = 55 s: cinco grupos completos (a los 10, 20, 30, 40 y 50 s).
        int[] tick = {0};
        Counts counts = run.run(1_100, r -> {
            tick[0]++;
            long batchesDue = tick[0] / ticksPerInterval;
            assertTrue(r.spawner.spawned() <= 3 * batchesDue, "spawned " + r.spawner.spawned() + " at tick " + tick[0]);
        });

        assertEquals(15, counts.spawned());
    }

    @Test
    void kindProportionsApproachWeights() {
        SimConfig cfg = SimConfig.load();
        Counts counts = new Run(cfg).run(20_000);

        long total = counts.spawnedByKind().values().stream().mapToLong(Integer::longValue).sum();
        assertEquals(counts.spawned(), total);
        double weightSum = cfg.spawnWeights().values().stream().mapToDouble(Double::doubleValue).sum();
        for (VehicleKind kind : VehicleKind.values()) {
            double expected = cfg.spawnWeights().get(kind) / weightSum;
            double actual = counts.spawnedByKind().get(kind) / (double) total;
            assertEquals(expected, actual, PROPORTION_TOLERANCE, kind + " over " + total + " vehicles");
        }
    }

    @Test
    void vehiclesLeaveAndCountersAddUp() {
        var run = new Run(SimConfig.load());
        Counts counts = run.run(6_000);

        long active = counts.active().values().stream().mapToLong(Integer::longValue).sum();
        assertTrue(counts.despawned() > 0);
        assertEquals(counts.spawned() - counts.despawned(), active);
        assertEquals(run.world.vehicleCount(), active);
    }

    @Test
    void vehiclesGoBetweenDistinctBorderIntersections() {
        var run = new Run(SimConfig.load());
        assertEquals(8, run.spawner.border().size());
        assertFalse(run.spawner.border().stream().anyMatch(i -> i.id().equals("I11")));

        run.run(2_000, r -> {
            for (Vehicle vehicle : r.world.vehicles()) {
                Intersection origin = vehicle.origin();
                assertTrue(r.spawner.border().contains(origin), origin.id());
                assertTrue(r.spawner.border().contains(vehicle.destination()), vehicle.destination().id());
                assertFalse(origin.equals(vehicle.destination()));
            }
        });
    }

    @Test
    void v2VehiclesGoFromAGatewayToAnotherAndLeaveThere() {
        var run = new Run(v2Config(), V2);
        assertTrue(run.spawner.usesGateways());
        assertEquals(12, run.spawner.border().size());
        assertTrue(run.spawner.border().stream().allMatch(Intersection::isGateway));
        Map<String, Vehicle> lastSeen = new HashMap<>();
        int[] leftAtGateway = {0};

        Counts counts = run.run(2_000, r -> {
            Map<String, Vehicle> now = new HashMap<>();
            for (Vehicle vehicle : r.world.vehicles()) {
                assertTrue(vehicle.origin().isGateway(), vehicle.origin().id());
                assertTrue(vehicle.destination().isGateway(), vehicle.destination().id());
                assertFalse(vehicle.origin().equals(vehicle.destination()));
                now.put(vehicle.id(), vehicle);
            }
            // El que desaparece iba por su última vía, hacia su gateway de destino.
            lastSeen.forEach((id, vehicle) -> {
                if (!now.containsKey(id)) {
                    assertEquals(vehicle.destination(), vehicle.currentRoad().to(), id);
                    assertTrue(vehicle.nextRoad().isEmpty(), id);
                    leftAtGateway[0]++;
                }
            });
            lastSeen.clear();
            lastSeen.putAll(now);
        });

        assertTrue(leftAtGateway[0] > 0, "no vehicle left the city");
        assertEquals(counts.despawned(), leftAtGateway[0]);
    }

    @Test
    void v2UsesAllTwelveGatewaysAsOrigin() {
        var run = new Run(v2Config(), V2);
        Set<String> origins = new HashSet<>();

        run.run(5_000, r -> r.world.vehicles().forEach(vehicle -> origins.add(vehicle.origin().id())));

        assertEquals(run.spawner.border().stream().map(Intersection::id).collect(Collectors.toSet()), origins);
    }

    @Test
    void v2SameSeedGivesSameVehicles() {
        List<String> first = spawnLog(new Run(v2Config(), V2), 1_500);
        List<String> second = spawnLog(new Run(v2Config(), V2), 1_500);

        assertFalse(first.isEmpty());
        assertEquals(first, second);
    }

    @Test
    void v2RoutesNeverCrossAParking() {
        // Una playa tiene una sola vía de doble sentido: pasar por ella obliga a volver por donde se entró.
        var run = new Run(v2Config(), V2);
        List<Intersection> gateways = run.spawner.border();
        for (String name : RouteStrategies.NAMES) {
            RouteStrategy strategy = run.spawner.strategyNamed(name);
            for (Intersection from : gateways) {
                for (Intersection to : gateways) {
                    if (from.equals(to)) {
                        continue;
                    }
                    List<Road> route = strategy.route(run.world.graph(), from, to);
                    assertFalse(route.isEmpty(), name + " " + from.id() + " -> " + to.id());
                    assertTrue(route.stream().noneMatch(road -> road.to().isParking()),
                            name + " " + from.id() + " -> " + to.id() + " crosses a parking");
                }
            }
        }
    }

    private static final String V2 = "maps/city-3x3-v2.json";

    private static SimConfig v2Config() {
        var props = new Properties();
        props.setProperty(SimConfig.ENGINE_PARALLEL, "false");
        props.setProperty(SimConfig.SPAWN_INTERVAL_MILLIS, "500");
        return SimConfig.from(props);
    }

    // Tipo, origen y destino de cada vehículo en el orden en que nace; sin el id, cuyo contador es global.
    private static List<String> spawnLog(Run run, int ticks) {
        List<String> log = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        run.run(ticks, r -> r.world.vehicles().forEach(vehicle -> {
            if (seen.add(vehicle.id())) {
                log.add(vehicle.kind() + " " + vehicle.origin().id() + "->" + vehicle.destination().id());
            }
        }));
        return log;
    }
}
