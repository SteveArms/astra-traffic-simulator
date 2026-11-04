package com.astra.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.astra.graph.Intersection;
import com.astra.graph.Road;
import com.astra.graph.RoadGraph;
import com.astra.model.Vehicle;
import com.astra.traffic.LightColor;
import com.astra.traffic.LightController;
import com.astra.traffic.TestLights;
import com.astra.traffic.TrafficLight;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * 150 vehículos en una malla de 6x4 (el mapa 3x3 no admite tantos a la vez). Los semáforos avanzan a mano cada
 * cierto número de ticks, así el color no cambia mientras se revisan los invariantes.
 */
@Timeout(120)
class EngineStressTest {

    private static final int TICKS = 3_000;
    private static final int MAX_VEHICLES = 150;
    private static final int TICKS_PER_LIGHT_PHASE = 160;
    private static final int COLUMNS = 6;
    private static final int ROWS = 4;
    private static final double SPACING = 160;
    private static final double SPEED_LIMIT = 14;
    private static final double GAP_TOLERANCE = 0.01;
    private static final double LINE_TOLERANCE = 1e-6;
    private static final double THROUGHPUT_TOLERANCE = 0.15;

    private record Before(Road road, double offset, boolean heldZone, LightColor color) {
    }

    private record Result(long despawned, int peak) {
    }

    @Test
    void parallelEngineKeepsInvariantsUnderLoad() {
        Result result = simulate(true, true);

        assertEquals(MAX_VEHICLES, result.peak(), "load never reached " + MAX_VEHICLES + " vehicles");
        assertTrue(result.despawned() > 0, "no vehicle left the map");
    }

    @Test
    void parallelThroughputMatchesSequentialWithin15Percent() {
        Result parallel = simulate(true, false);
        Result sequential = simulate(false, false);

        assertTrue(sequential.despawned() > 0);
        double ratio = parallel.despawned() / (double) sequential.despawned();
        assertTrue(Math.abs(ratio - 1) <= THROUGHPUT_TOLERANCE,
                "parallel " + parallel.despawned() + " vs sequential " + sequential.despawned());
    }

    private Result simulate(boolean parallel, boolean checkInvariants) {
        var props = new Properties();
        props.setProperty(SimConfig.ENGINE_PARALLEL, Boolean.toString(parallel));
        props.setProperty(SimConfig.VEHICLE_MAX_COUNT, Integer.toString(MAX_VEHICLES));
        props.setProperty(SimConfig.SPAWN_INTERVAL_MILLIS, "100");
        SimConfig cfg = SimConfig.from(props);
        RoadGraph graph = grid();
        var lights = new LightController(graph, cfg);
        var world = new WorldState(graph, cfg, lights, new IntersectionZones(graph, cfg));
        var engine = new SimulationEngine(cfg, world, new VehicleSpawner(world, cfg));
        assertEquals(parallel, world.isParallel());
        int peak = 0;
        try {
            for (int tick = 1; tick <= TICKS; tick++) {
                Map<String, Before> before = checkInvariants ? capture(world, lights) : Map.of();
                engine.step();
                peak = Math.max(peak, world.vehicleCount());
                assertTrue(world.vehicleCount() <= MAX_VEHICLES, "vehicles " + world.vehicleCount());
                if (checkInvariants) {
                    checkZones(world, tick);
                    checkGaps(world, cfg, tick);
                    checkRedLights(world, lights, cfg, before, tick);
                }
                if (tick % TICKS_PER_LIGHT_PHASE == 0) {
                    for (TrafficLight light : lights.lights()) {
                        TestLights.force(light, light.state().next());
                    }
                }
            }
        } finally {
            engine.stop();
        }
        return new Result(world.despawned(), peak);
    }

    private static Map<String, Before> capture(WorldState world, LightController lights) {
        Map<String, Before> before = new HashMap<>();
        for (Vehicle vehicle : world.vehicles()) {
            Road road = vehicle.currentRoad();
            boolean held = world.zones().zoneOf(road.to()).contains(vehicle.id());
            before.put(vehicle.id(), new Before(road, vehicle.offset(), held, lights.colorFor(road)));
        }
        return before;
    }

    private static void checkZones(WorldState world, int tick) {
        for (IntersectionZone zone : world.zones().zones()) {
            assertTrue(zone.occupancy() <= zone.capacity(),
                    "tick " + tick + ": zone " + zone.intersection().id() + " has " + zone.occupancy());
        }
    }

    private static void checkGaps(WorldState world, SimConfig cfg, int tick) {
        Map<Road, List<Vehicle>> byRoad = new HashMap<>();
        for (Vehicle vehicle : world.vehicles()) {
            byRoad.computeIfAbsent(vehicle.currentRoad(), r -> new ArrayList<>()).add(vehicle);
        }
        for (List<Vehicle> onRoad : byRoad.values()) {
            onRoad.sort(Comparator.comparingDouble(Vehicle::offset).reversed());
            for (int i = 1; i < onRoad.size(); i++) {
                Vehicle leader = onRoad.get(i - 1);
                Vehicle follower = onRoad.get(i);
                double gap = leader.rearOffset() - follower.offset();
                assertTrue(gap >= cfg.minGap() - GAP_TOLERANCE, "tick " + tick + ": gap " + gap + " between "
                        + leader.id() + " and " + follower.id() + " on " + leader.currentRoad().id());
            }
        }
    }

    // Cruzar la línea sin tener la zona y con el semáforo en rojo antes y después del tick es pasarse el rojo.
    private static void checkRedLights(WorldState world, LightController lights, SimConfig cfg,
            Map<String, Before> before, int tick) {
        Map<String, Vehicle> now = new HashMap<>();
        world.vehicles().forEach(v -> now.put(v.id(), v));
        before.forEach((id, b) -> {
            double stopLine = b.road().length() - cfg.stopLineOffset();
            if (b.offset() > stopLine + LINE_TOLERANCE || b.heldZone() || b.color() != LightColor.RED) {
                return;
            }
            Vehicle vehicle = now.get(id);
            boolean crossed = vehicle == null || !vehicle.currentRoad().equals(b.road())
                    || vehicle.offset() > stopLine + LINE_TOLERANCE;
            if (crossed && lights.colorFor(b.road()) == LightColor.RED) {
                fail("tick " + tick + ": " + id + " ran the red light on " + b.road().id());
            }
        });
    }

    private static RoadGraph grid() {
        var graph = new RoadGraph();
        var nodes = new Intersection[ROWS][COLUMNS];
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLUMNS; c++) {
                nodes[r][c] = new Intersection("G" + r + c, c * SPACING, r * SPACING);
                graph.addVertex(nodes[r][c]);
            }
        }
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLUMNS; c++) {
                if (c + 1 < COLUMNS) {
                    graph.addTwoWayRoad("H" + r + c, nodes[r][c], nodes[r][c + 1], SPEED_LIMIT, 1);
                }
                if (r + 1 < ROWS) {
                    graph.addTwoWayRoad("V" + r + c, nodes[r][c], nodes[r + 1][c], SPEED_LIMIT, 1);
                }
            }
        }
        assertFalse(graph.edges().isEmpty());
        return graph;
    }
}
