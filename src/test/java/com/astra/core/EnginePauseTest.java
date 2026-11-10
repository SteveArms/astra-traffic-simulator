package com.astra.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.astra.command.CommandBus;
import com.astra.graph.Intersection;
import com.astra.graph.Road;
import com.astra.graph.RoadGraph;
import com.astra.model.Car;
import com.astra.model.VehicleSpec;
import com.astra.routing.ShortestDistanceStrategy;
import com.astra.traffic.LightController;
import java.util.List;
import java.util.Properties;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10)
class EnginePauseTest {

    private static final double DELTA = 1e-9;
    private static final double DT = 0.05;
    // Aceleración enorme: llega a la velocidad máxima en el primer tick y después avanza a velocidad constante.
    private static final VehicleSpec INSTANT = new VehicleSpec(14, 1_000, 6, 12);

    private Road road;
    private WorldState world;
    private SimulationControl control;
    private SimulationEngine engine;

    @BeforeEach
    void setUp() {
        var graph = new RoadGraph();
        var a = new Intersection("A", 0, 0);
        var b = new Intersection("B", 10_000, 0);
        graph.addVertex(a);
        graph.addVertex(b);
        road = new Road("AB", a, b, 10_000, 20, 1);
        graph.addEdge(road);
        var cfg = SimConfig.from(new Properties());
        world = new WorldState(graph, cfg, new LightController(graph, cfg), new IntersectionZones(graph, cfg));
        control = new SimulationControl();
        engine = new SimulationEngine(cfg, world, null, new CommandBus(), control);
        var car = new Car(INSTANT, new ShortestDistanceStrategy(), a, b);
        car.assignRoute(List.of(road));
        world.addVehicle(car);
    }

    @AfterEach
    void tearDown() {
        engine.stop();
    }

    @Test
    void pauseFreezesWorldButTicksKeepRunning() throws InterruptedException {
        engine.start();
        awaitUntil(() -> x(engine.latestSnapshot()) > 0);

        control.pauseGate().pause();
        awaitUntil(() -> engine.latestSnapshot().paused());
        WorldSnapshot frozen = engine.latestSnapshot();
        Thread.sleep(300);
        WorldSnapshot later = engine.latestSnapshot();

        assertTrue(later.paused());
        assertTrue(later.tick() > frozen.tick(), "ticks keep running while paused");
        assertEquals(frozen.simTimeSeconds(), later.simTimeSeconds(), DELTA);
        assertEquals(x(frozen), x(later), DELTA);
    }

    @Test
    void requestedStepsAdvanceExactlyThatManyTicks() throws InterruptedException {
        engine.start();
        control.pauseGate().pause();
        awaitUntil(() -> engine.latestSnapshot().paused());
        WorldSnapshot frozen = engine.latestSnapshot();

        control.requestStep();
        control.requestStep();
        awaitUntil(() -> control.pendingSteps() == 0);
        long consumedAt = engine.latestSnapshot().tick();
        // El segundo paso pudo consumirse en un tick que aún no publicó su snapshot.
        awaitUntil(() -> engine.latestSnapshot().tick() > consumedAt);
        Thread.sleep(200);
        WorldSnapshot stepped = engine.latestSnapshot();

        assertTrue(stepped.paused());
        assertEquals(frozen.simTimeSeconds() + 2 * DT, stepped.simTimeSeconds(), DELTA);
        assertTrue(x(stepped) > x(frozen));
    }

    @Test
    void resumeMovesVehiclesAgain() throws InterruptedException {
        engine.start();
        control.pauseGate().pause();
        awaitUntil(() -> engine.latestSnapshot().paused());
        WorldSnapshot frozen = engine.latestSnapshot();

        control.pauseGate().resume();
        awaitUntil(() -> !engine.latestSnapshot().paused());
        Thread.sleep(200);
        WorldSnapshot resumed = engine.latestSnapshot();

        assertFalse(resumed.paused());
        assertTrue(resumed.simTimeSeconds() > frozen.simTimeSeconds());
        assertTrue(x(resumed) > x(frozen));
    }

    @Test
    void doubleSpeedDoublesDistancePerTick() {
        for (int i = 0; i < 3; i++) {
            engine.step();
        }
        double x0 = x(engine.latestSnapshot());
        engine.step();
        double normal = x(engine.latestSnapshot()) - x0;

        control.setSpeed(2.0);
        double x1 = x(engine.latestSnapshot());
        engine.step();
        WorldSnapshot fast = engine.latestSnapshot();

        assertTrue(normal > 0);
        assertEquals(2 * normal, x(fast) - x1, 1e-6);
        assertEquals(2.0, fast.speedFactor(), DELTA);
    }

    @Test
    void speedIsClampedToItsRange() {
        control.setSpeed(10);
        assertEquals(SimulationControl.MAX_SPEED, control.speedFactor(), DELTA);
        control.setSpeed(0);
        assertEquals(SimulationControl.MIN_SPEED, control.speedFactor(), DELTA);
        assertThrows(IllegalArgumentException.class, () -> control.setSpeed(Double.NaN));
    }

    private static double x(WorldSnapshot snapshot) {
        return snapshot.vehicles().isEmpty() ? 0 : snapshot.vehicles().get(0).x();
    }

    private static void awaitUntil(BooleanSupplier condition) throws InterruptedException {
        while (!condition.getAsBoolean()) {
            Thread.sleep(5);
        }
    }
}
