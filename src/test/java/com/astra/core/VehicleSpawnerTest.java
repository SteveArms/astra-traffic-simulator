package com.astra.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.astra.graph.Intersection;
import com.astra.graph.RoadGraph;
import com.astra.model.Vehicle;
import com.astra.model.VehicleKind;
import com.astra.traffic.LightController;
import com.astra.traffic.TestLights;
import com.astra.traffic.TrafficLight;
import com.astra.util.MapLoader;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;
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
            RoadGraph graph = MapLoader.load(cfg.mapFile()).graph();
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
}
