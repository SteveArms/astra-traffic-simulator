package com.astra.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.astra.model.Vehicle;
import com.astra.util.MapLoader;
import java.util.List;
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
        world = new WorldState(MapLoader.load(config.mapFile()).graph());
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
}
