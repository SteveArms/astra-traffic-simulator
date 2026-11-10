package com.astra.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.astra.graph.Intersection;
import com.astra.graph.Road;
import com.astra.graph.RoadGraph;
import com.astra.model.Car;
import com.astra.model.Vehicle;
import com.astra.model.VehicleKind;
import com.astra.routing.ShortestDistanceStrategy;
import com.astra.traffic.Axis;
import com.astra.traffic.GreenState;
import com.astra.traffic.LightController;
import com.astra.traffic.TestLights;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Cruce en + con el semáforo fijo en verde EW: los que llegan por el norte esperan en rojo. */
@Timeout(10)
class QueueSensorTest {

    private static final int MAX_STEPS = 2_000;
    private static final double SPACING = 30;

    private SimConfig cfg;
    private Intersection center;
    private Road northIn;
    private Road centerSouth;
    private WorldState world;
    private SimulationEngine engine;

    @BeforeEach
    void setUp() {
        cfg = SimConfig.from(new Properties());
        var graph = new RoadGraph();
        center = new Intersection("C", 200, 200);
        var west = new Intersection("W", 0, 200);
        var east = new Intersection("E", 400, 200);
        var north = new Intersection("N", 200, 0);
        var south = new Intersection("S", 200, 400);
        List.of(center, west, east, north, south).forEach(graph::addVertex);
        graph.addTwoWayRoad("WC", west, center, cfg.carMaxSpeed(), 1);
        graph.addTwoWayRoad("CE", center, east, cfg.carMaxSpeed(), 1);
        northIn = graph.addTwoWayRoad("NC", north, center, cfg.carMaxSpeed(), 1).get(0);
        centerSouth = graph.addTwoWayRoad("CS", center, south, cfg.carMaxSpeed(), 1).get(0);
        var lights = new LightController(graph, cfg);
        TestLights.force(lights.lightAt(center).orElseThrow(), new GreenState(Axis.EW));
        world = new WorldState(graph, cfg, lights, new IntersectionZones(graph, cfg));
        engine = new SimulationEngine(cfg, world);
    }

    @AfterEach
    void tearDown() {
        engine.stop();
    }

    @Test
    void waitingVehiclesAreCountedByAxis() {
        List<Vehicle> cars = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Vehicle car = addNorthCar();
            cars.add(car);
            stepUntil(() -> car.offset() > SPACING);
        }

        stepUntil(() -> cars.stream().allMatch(c -> c.remainingOnRoad() <= cfg.smartQueueRadius()));

        assertEquals(3, world.queueLength(center, Axis.NS));
        assertEquals(0, world.queueLength(center, Axis.EW));
        LightView view = engine.latestSnapshot().lights().get(0);
        assertEquals(3, view.queueNs());
        assertEquals(0, view.queueEw());
    }

    @Test
    void onlyVehiclesInsideTheRadiusCount() {
        Vehicle car = addNorthCar();
        engine.step();
        assertTrue(car.remainingOnRoad() > cfg.smartQueueRadius());
        assertEquals(0, world.queueLength(center, Axis.NS));

        stepUntil(() -> car.remainingOnRoad() <= cfg.smartQueueRadius() && car.speed() > 1);

        assertEquals(1, world.queueLength(center, Axis.NS));
    }

    private Vehicle addNorthCar() {
        Vehicle car = new Car(cfg.specFor(VehicleKind.CAR), new ShortestDistanceStrategy(), northIn.from(),
                centerSouth.to());
        car.assignRoute(List.of(northIn, centerSouth));
        world.addVehicle(car);
        return car;
    }

    private void stepUntil(BooleanSupplier condition) {
        for (int i = 0; i < MAX_STEPS && !condition.getAsBoolean(); i++) {
            engine.step();
        }
        if (!condition.getAsBoolean()) {
            throw new AssertionError("condition not reached in " + MAX_STEPS + " steps");
        }
    }
}
