package com.astra.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.astra.graph.Intersection;
import com.astra.graph.Road;
import com.astra.graph.RoadGraph;
import com.astra.model.Ambulance;
import com.astra.model.Bus;
import com.astra.model.Car;
import com.astra.model.Vehicle;
import com.astra.model.VehicleKind;
import com.astra.routing.LeastCongestedStrategy;
import com.astra.routing.RoadOccupancy;
import com.astra.routing.RouteStrategy;
import com.astra.routing.ShortestDistanceStrategy;
import com.astra.traffic.Axis;
import com.astra.traffic.GreenState;
import com.astra.traffic.LightColor;
import com.astra.traffic.LightController;
import com.astra.traffic.RedState;
import com.astra.traffic.TestLights;
import com.astra.traffic.TrafficLight;
import com.astra.traffic.YellowState;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Cruce en forma de + con semáforo en el centro C; los hilos de luces nunca se inician. */
@Timeout(10)
class DrivingTest {

    private static final double EPS = 1e-6;
    private static final int MAX_STEPS = 2_000;
    private static final RouteStrategy SHORTEST = new ShortestDistanceStrategy();

    private SimConfig cfg;
    private Intersection center;
    private Road westIn;
    private Road eastOut;
    private Road eastIn;
    private Road westOut;
    private TrafficLight light;
    private IntersectionZone zone;
    private WorldState world;
    private SimulationEngine engine;

    private void build(SimConfig config) {
        cfg = config;
        var graph = new RoadGraph();
        center = new Intersection("C", 200, 200);
        var west = new Intersection("W", 0, 200);
        var east = new Intersection("E", 400, 200);
        var north = new Intersection("N", 200, 0);
        var south = new Intersection("S", 200, 400);
        List.of(center, west, east, north, south).forEach(graph::addVertex);
        List<Road> we = graph.addTwoWayRoad("WC", west, center, cfg.carMaxSpeed(), 1);
        List<Road> ce = graph.addTwoWayRoad("CE", center, east, cfg.carMaxSpeed(), 1);
        graph.addTwoWayRoad("NC", north, center, cfg.carMaxSpeed(), 1);
        graph.addTwoWayRoad("CS", center, south, cfg.carMaxSpeed(), 1);
        westIn = we.get(0);
        westOut = we.get(1);
        eastOut = ce.get(0);
        eastIn = ce.get(1);

        var lights = new LightController(graph, cfg);
        var zones = new IntersectionZones(graph, cfg);
        light = lights.lightAt(center).orElseThrow();
        zone = zones.zoneOf(center);
        world = new WorldState(graph, cfg, lights, zones);
        engine = new SimulationEngine(cfg, world);
    }

    private Vehicle addCar(Road... route) {
        return add(new Car(cfg.specFor(VehicleKind.CAR), SHORTEST, route[0].from(), route[route.length - 1].to()),
                route);
    }

    private Vehicle add(Vehicle vehicle, Road... route) {
        vehicle.assignRoute(List.of(route));
        world.addVehicle(vehicle);
        return vehicle;
    }

    private double stopLine(Road road) {
        return road.length() - cfg.stopLineOffset();
    }

    private void stepUntil(BooleanSupplier done) {
        for (int i = 0; i < MAX_STEPS && !done.getAsBoolean(); i++) {
            engine.step();
        }
        assertTrue(done.getAsBoolean(), "condition not reached in " + MAX_STEPS + " steps");
    }

    @Test
    void stopsBeforeLineOnRed() {
        build(SimConfig.load());
        TestLights.force(light, new RedState(Axis.NS));
        Vehicle car = addCar(westIn, eastOut);

        for (int i = 0; i < 500; i++) {
            engine.step();
            assertTrue(car.offset() <= stopLine(westIn) + EPS, "offset " + car.offset());
        }

        assertEquals(westIn, car.currentRoad());
        assertEquals(0, car.speed(), EPS);
        assertEquals(stopLine(westIn), car.offset(), 0.5);
        assertEquals(0, zone.occupancy());
    }

    @Test
    void crossesOnGreenAndFreesZoneAfterBox() {
        build(SimConfig.load());
        TestLights.force(light, new RedState(Axis.NS));
        Vehicle car = addCar(westIn, eastOut);
        for (int i = 0; i < 500; i++) {
            engine.step();
        }

        TestLights.force(light, new GreenState(Axis.EW));
        stepUntil(() -> zone.contains(car.id()));
        assertEquals(1, zone.occupancy());

        stepUntil(() -> car.currentRoad().equals(eastOut) && car.offset() > cfg.boxLength());
        assertEquals(0, zone.occupancy());
    }

    @Test
    void followerKeepsMinGap() {
        build(SimConfig.load());
        TestLights.force(light, new RedState(Axis.NS));
        Vehicle leader = addCar(westIn, eastOut);
        stepUntil(() -> leader.rearOffset() > cfg.minGap() + 1);
        Vehicle follower = addCar(westIn, eastOut);

        for (int i = 0; i < 1_000; i++) {
            engine.step();
            double gap = leader.rearOffset() - follower.offset();
            assertTrue(gap >= cfg.minGap() - EPS, "gap " + gap + " at step " + i);
        }
        assertEquals(0, follower.speed(), EPS);
    }

    @Test
    void busBehindCarKeepsGapToItsRearBumper() {
        build(SimConfig.load());
        TestLights.force(light, new RedState(Axis.NS));
        Vehicle car = addCar(westIn, eastOut);
        stepUntil(() -> car.rearOffset() > cfg.minGap() + 1);
        Vehicle bus = add(new Bus(cfg.specFor(VehicleKind.BUS), SHORTEST, westIn.from(), eastOut.to()),
                westIn, eastOut);

        for (int i = 0; i < 1_500; i++) {
            engine.step();
            double gap = car.rearOffset() - bus.offset();
            assertTrue(gap >= cfg.minGap() - EPS, "gap " + gap + " at step " + i);
        }
        assertEquals(0, car.speed(), EPS);
        assertEquals(0, bus.speed(), EPS);
        // Detenidos: el frente del bus queda a minGap del parachoques trasero del auto, no de su frente.
        assertEquals(cfg.minGap() + car.length(), car.offset() - bus.offset(), 0.5);
    }

    @Test
    void ambulanceArrivingOnRedCrossesWithoutStopping() {
        build(SimConfig.load());
        TestLights.force(light, new GreenState(Axis.NS));
        Vehicle ambulance = add(new Ambulance(cfg.specFor(VehicleKind.AMBULANCE), SHORTEST, westIn.from(),
                eastOut.to()), westIn, eastOut);
        engine.step();

        double minSpeed = Double.POSITIVE_INFINITY;
        boolean sawEmergency = false;
        while (!(ambulance.currentRoad().equals(eastOut) && ambulance.offset() > cfg.boxLength())) {
            engine.step();
            minSpeed = Math.min(minSpeed, ambulance.speed());
            sawEmergency |= "EMERGENCY-EW".equals(light.state().name());
            assertTrue(world.vehicles().contains(ambulance), "ambulance vanished");
        }

        assertTrue(minSpeed > 0, "ambulance stopped");
        assertTrue(sawEmergency);
        assertEquals(new YellowState(Axis.EW), light.state());
        assertFalse(light.isPreempted());
        assertEquals(0, zone.occupancy());
    }

    @Test
    void adaptiveStrategyReroutesOnEveryNewRoad() {
        build(SimConfig.load());
        TestLights.force(light, new GreenState(Axis.EW));
        var calls = new AtomicInteger();
        var adaptive = new LeastCongestedStrategy(() -> {
            calls.incrementAndGet();
            return RoadOccupancy.EMPTY;
        }, cfg.congestionFactor());
        Vehicle car = add(new Car(cfg.specFor(VehicleKind.CAR), adaptive, westIn.from(), eastOut.to()),
                westIn, eastOut);

        stepUntil(() -> car.currentRoad().equals(eastOut));

        assertEquals(1, calls.get());
        assertEquals(List.of(eastOut), car.route());
        assertEquals(eastOut, car.currentRoad());
    }

    @Test
    void secondCarWaitsWhileZoneIsFull() {
        var props = new Properties();
        props.setProperty(SimConfig.INTERSECTION_CAPACITY, "1");
        build(SimConfig.from(props));
        TestLights.force(light, new GreenState(Axis.EW));
        Vehicle first = addCar(westIn, eastOut);
        Vehicle second = addCar(eastIn, westOut);

        stepUntil(() -> zone.contains(first.id()));
        boolean secondWaited = false;
        while (zone.contains(first.id())) {
            assertEquals(1, zone.occupancy());
            assertEquals(eastIn, second.currentRoad());
            assertTrue(second.offset() <= stopLine(eastIn) + EPS, "second crossed the line");
            secondWaited |= second.speed() < EPS;
            engine.step();
        }
        assertTrue(secondWaited);

        stepUntil(() -> second.currentRoad().equals(westOut));
    }

    @Test
    void snapshotShowsLightColorsAndZoneOccupancy() {
        build(SimConfig.load());
        TestLights.force(light, new GreenState(Axis.EW));
        Vehicle car = addCar(westIn, eastOut);
        stepUntil(() -> zone.contains(car.id()));

        LightView view = engine.latestSnapshot().lights().get(0);

        assertEquals(1, engine.latestSnapshot().lightCount());
        assertEquals("C", view.intersectionId());
        assertEquals(LightColor.GREEN, view.ew());
        assertEquals(LightColor.RED, view.ns());
        assertEquals("GREEN(EW)", view.stateName());
        assertEquals(1, view.zoneOccupancy());
        assertEquals(cfg.intersectionCapacity(), view.zoneCapacity());
    }

    @Test
    void lastRoadAlsoStopsOnRed() {
        build(SimConfig.load());
        TestLights.force(light, new RedState(Axis.NS));
        Vehicle car = addCar(westIn);

        for (int i = 0; i < 500; i++) {
            engine.step();
            assertTrue(car.offset() <= stopLine(westIn) + EPS, "offset " + car.offset());
        }
        assertTrue(world.vehicles().contains(car));
        assertEquals(0, zone.occupancy());

        TestLights.force(light, new GreenState(Axis.EW));
        stepUntil(() -> !world.vehicles().contains(car));
        assertEquals(0, zone.occupancy());
    }

    @Test
    void newCarKeepsOriginZoneUntilItLeavesTheBox() {
        build(SimConfig.load());
        Vehicle car = addCar(eastOut);
        assertTrue(zone.contains(car.id()), "new car does not hold the zone inside the box");

        stepUntil(() -> car.offset() > cfg.boxLength());
        assertEquals(0, zone.occupancy());
    }

    @Test
    void finishedVehicleIsRemovedAndCounted() {
        build(SimConfig.load());
        TestLights.force(light, new GreenState(Axis.EW));
        Vehicle car = addCar(westIn);

        stepUntil(() -> !world.vehicles().contains(car));

        assertEquals(1, world.spawned());
        assertEquals(1, world.despawned());
        assertEquals(0, zone.occupancy());
        WorldSnapshot snapshot = engine.latestSnapshot();
        assertEquals(1, snapshot.despawned());
        assertEquals(0, snapshot.activeCount(VehicleKind.CAR));
    }

    @Test
    void occupancyCountsVehiclesPerRoadAndCapacity() {
        build(SimConfig.load());
        TestLights.force(light, new RedState(Axis.NS));
        Vehicle first = addCar(westIn, eastOut);
        stepUntil(() -> first.rearOffset() > cfg.minGap() + 1);
        addCar(westIn, eastOut);
        engine.step();

        double capacity = westIn.length() / (cfg.minGap() + cfg.specFor(VehicleKind.CAR).length()) * westIn.lanes();
        assertEquals(2 / capacity, world.currentOccupancy().of(westIn), 1e-9);
        assertEquals(0, world.currentOccupancy().of(eastOut));
    }
}
