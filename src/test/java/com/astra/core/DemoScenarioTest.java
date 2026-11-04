package com.astra.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.astra.graph.Intersection;
import com.astra.graph.Road;
import com.astra.graph.RoadGraph;
import com.astra.model.Vehicle;
import com.astra.model.VehicleKind;
import com.astra.routing.RouteStrategy;
import com.astra.routing.ShortestDistanceStrategy;
import com.astra.traffic.LightController;
import com.astra.util.MapLoader;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10)
class DemoScenarioTest {

    private static final long SEED = 7;
    private static final RouteStrategy SHORTEST = new ShortestDistanceStrategy();

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
            assertEquals(VehicleKind.CAR, vehicle.kind());
            assertEquals("SHORTEST", vehicle.strategy().name());
            assertEquals(vehicle.origin(), vehicle.route().get(0).from());
            assertNotEquals(vehicle.origin(), vehicle.destination());
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
    void arrivedCarsAreReplacedFromTheirDestination() {
        List<Vehicle> initial = List.copyOf(world.vehicles());
        var engine = new SimulationEngine(config, world);

        for (int i = 0; i < 2_000; i++) {
            engine.step();
        }

        assertEquals(config.demoCarCount(), world.vehicleCount());
        assertTrue(world.vehicles().stream().anyMatch(v -> !initial.contains(v)), "no car was replaced");
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

        for (long seed = 0; seed < 50; seed++) {
            Intersection destination = DemoScenario.randomDestination(graph, SHORTEST, all, new Random(seed),
                    intoCorner.to(), intoCorner).orElseThrow();
            List<Road> route = SHORTEST.route(graph, intoCorner.to(), destination);
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

        Optional<Intersection> destination =
                DemoScenario.randomDestination(line, SHORTEST, List.of(a, b), new Random(1), b, ab);

        assertEquals(Optional.of(a), destination);
    }

    private static Road road(RoadGraph graph, String from, String to) {
        return graph.edges().stream()
                .filter(r -> r.from().id().equals(from) && r.to().id().equals(to)).findFirst().orElseThrow();
    }
}
