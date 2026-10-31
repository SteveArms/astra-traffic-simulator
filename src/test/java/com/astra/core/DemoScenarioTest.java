package com.astra.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.astra.graph.Intersection;
import com.astra.graph.Road;
import com.astra.graph.RoadGraph;
import com.astra.graph.algo.Dijkstra;
import com.astra.model.Vehicle;
import com.astra.traffic.LightController;
import com.astra.util.MapLoader;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10)
class DemoScenarioTest {

    private static final long SEED = 7;

    private SimConfig config;
    private WorldState world;

    @BeforeEach
    void setUp() {
        config = SimConfig.load();
        var graph = MapLoader.load(config.mapFile()).graph();
        world = new WorldState(graph, config, new LightController(graph, config), new IntersectionZones(graph, config));
        DemoScenario.seed(world, config, SEED);
    }

    @Test
    void seedCreatesConfiguredCarsWithRoutes() {
        assertEquals(config.demoCarCount(), world.vehicleCount());
        for (Vehicle vehicle : world.vehicles()) {
            assertFalse(vehicle.route().isEmpty());
            assertFalse(vehicle.route().get(0).from().equals(vehicle.destination()));
        }
    }

    @Test
    void carsStartFromDifferentIntersections() {
        long distinctOrigins = world.vehicles().stream()
                .map(vehicle -> vehicle.route().get(0).from())
                .distinct()
                .count();

        assertEquals(config.demoCarCount(), distinctOrigins);
    }

    @Test
    void carsGetNewRoutesWhenTheyArrive() {
        List<Vehicle> initial = List.copyOf(world.vehicles());
        var engine = new SimulationEngine(config, world);

        for (int i = 0; i < 2_000; i++) {
            engine.step();
        }

        assertEquals(initial, world.vehicles());
        for (Vehicle vehicle : world.vehicles()) {
            assertFalse(vehicle.route().isEmpty());
            assertFalse(vehicle.isFinished());
        }
    }

    @Test
    void newRouteNeverStartsWithUTurnWhenThereIsAnotherExit() {
        RoadGraph graph = world.graph();
        Road intoCorner = road(graph, "I01", "I00");
        List<Intersection> all = List.copyOf(graph.vertices());
        var dijkstra = new Dijkstra<Intersection, Road>();

        for (long seed = 0; seed < 50; seed++) {
            List<Road> route = DemoScenario.randomRoute(graph, dijkstra, all, new Random(seed), intoCorner.to(),
                    intoCorner).orElseThrow();
            assertNotEquals("I01", route.get(0).to().id(), "U-turn with seed " + seed);
        }
    }

    @Test
    void uTurnIsAllowedInDeadEnd() {
        var line = new RoadGraph();
        var a = new Intersection("A", 0, 0);
        var b = new Intersection("B", 100, 0);
        List.of(a, b).forEach(line::addVertex);
        Road ab = line.addTwoWayRoad("AB", a, b, 10, 1).get(0);

        List<Road> route = DemoScenario.randomRoute(line, new Dijkstra<>(), List.of(a, b), new Random(1), b, ab)
                .orElseThrow();

        assertEquals(List.of(a), route.stream().map(Road::to).toList());
    }

    private static Road road(RoadGraph graph, String from, String to) {
        return graph.edges().stream()
                .filter(r -> r.from().id().equals(from) && r.to().id().equals(to)).findFirst().orElseThrow();
    }
}
