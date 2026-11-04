package com.astra.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.astra.graph.Intersection;
import com.astra.graph.Road;
import com.astra.graph.RoadGraph;
import com.astra.model.Car;
import com.astra.model.Vehicle;
import com.astra.model.VehicleSpec;
import com.astra.routing.ShortestDistanceStrategy;
import com.astra.traffic.LightController;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(5)
class SimulationEngineTest {

    private static final double DELTA = 1e-9;
    private static final double MAX_SPEED = 14;
    private static final double ACCELERATION = 3;
    private static final double DECELERATION = 6;
    private static final double LENGTH = 12;

    private Road longRoad;
    private Road shortRoad;
    private WorldState world;
    private SimulationEngine engine;

    @BeforeEach
    void setUp() {
        var graph = new RoadGraph();
        var a = new Intersection("A", 0, 0);
        var b = new Intersection("B", 10_000, 0);
        var c = new Intersection("C", 10_000, 1);
        List.of(a, b, c).forEach(graph::addVertex);
        longRoad = new Road("AB", a, b, 10_000, 20, 1);
        shortRoad = new Road("BC", b, c, 1, 20, 1);
        graph.addEdge(longRoad);
        graph.addEdge(shortRoad);
        var cfg = SimConfig.from(new Properties());
        world = new WorldState(graph, cfg, new LightController(graph, cfg), new IntersectionZones(graph, cfg));
        engine = new SimulationEngine(cfg, world);
    }

    @AfterEach
    void tearDown() {
        engine.stop();
    }

    @Test
    void stepAdvancesTickAndMovesVehicle() {
        Vehicle car = car(longRoad, ACCELERATION);
        world.addVehicle(car);
        int steps = 20;

        for (int i = 0; i < steps; i++) {
            engine.step();
        }

        WorldSnapshot snapshot = engine.latestSnapshot();
        assertEquals(steps, snapshot.tick());
        assertEquals(steps * 0.05, snapshot.simTimeSeconds(), DELTA);
        assertEquals(1, snapshot.vehicleCount());
        assertTrue(snapshot.vehicles().get(0).x() > 0);
    }

    @Test
    void startRunsTicksAndStopKillsThread() throws InterruptedException {
        assertEquals(WorldSnapshot.EMPTY, engine.latestSnapshot());

        engine.start();
        assertTrue(engine.isRunning());
        Thread.sleep(300);
        assertTrue(engine.latestSnapshot().tick() > 0);

        engine.stop();
        assertFalse(engine.isRunning());
        assertTrue(engineThreadGone());
        engine.stop();
    }

    @Test
    void startTwiceThrows() {
        engine.start();

        assertThrows(IllegalStateException.class, engine::start);
    }

    @Test
    void stepWhileRunningThrows() {
        engine.start();

        assertThrows(IllegalStateException.class, engine::step);
    }

    @Test
    void finishedVehicleIsRemovedAndHookCanAddReplacement() {
        Vehicle leaving = car(shortRoad, 1_000);
        Vehicle replaced = car(shortRoad, 1_000);
        Vehicle replacement = car(longRoad, ACCELERATION);
        List<Vehicle> finished = new ArrayList<>();
        world.setOnVehicleFinished(vehicle -> {
            finished.add(vehicle);
            if (vehicle == replaced) {
                world.addVehicle(replacement);
            }
        });
        world.addVehicle(leaving);
        world.addVehicle(replaced);

        // Con minGap, replaced espera a que leaving salga de la vía.
        for (int i = 0; i < 100; i++) {
            engine.step();
        }

        assertEquals(List.of(leaving, replaced), finished);
        assertEquals(1, engine.latestSnapshot().vehicleCount());
        assertEquals(replacement.id(), engine.latestSnapshot().vehicles().get(0).id());
    }

    private static Vehicle car(Road road, double acceleration) {
        var spec = new VehicleSpec(MAX_SPEED, acceleration, DECELERATION, LENGTH);
        var car = new Car(spec, new ShortestDistanceStrategy(), road.from(), road.to());
        car.assignRoute(List.of(road));
        return car;
    }

    // El hilo tarda unos ms en morir tras awaitTermination.
    private static boolean engineThreadGone() throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            boolean alive = Thread.getAllStackTraces().keySet().stream()
                    .anyMatch(t -> t.getName().equals(SimulationEngine.THREAD_NAME) && t.isAlive());
            if (!alive) {
                return true;
            }
            Thread.sleep(10);
        }
        return false;
    }
}
