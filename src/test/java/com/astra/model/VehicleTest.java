package com.astra.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.astra.graph.Graph;
import com.astra.graph.Intersection;
import com.astra.graph.Road;
import com.astra.graph.RoadGraph;
import com.astra.routing.RouteStrategy;
import com.astra.routing.ShortestDistanceStrategy;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VehicleTest {

    private static final double DELTA = 1e-9;
    private static final double MAX_SPEED = 14;
    private static final double ACCELERATION = 3;
    private static final double DECELERATION = 6;
    private static final double LENGTH = 12;
    private static final double FREE = Double.POSITIVE_INFINITY;
    // Llega al tope en el primer update.
    private static final double INSTANT_ACCELERATION = 1_000;
    private static final VehicleSpec SPEC = new VehicleSpec(MAX_SPEED, ACCELERATION, DECELERATION, LENGTH);
    private static final RouteStrategy SHORTEST = new ShortestDistanceStrategy();

    /** Devuelve siempre la misma ruta y cuenta las llamadas. */
    private static final class FixedStrategy implements RouteStrategy {
        private final List<Road> route;
        private int calls;

        FixedStrategy(List<Road> route) {
            this.route = route;
        }

        @Override
        public List<Road> route(Graph<Intersection, Road> g, Intersection from, Intersection to) {
            calls++;
            return route;
        }

        @Override
        public String name() {
            return "FIXED";
        }
    }

    private RoadGraph graph;
    private Intersection a;
    private Intersection b;
    private Intersection c;
    private Intersection d;
    private Road ab;
    private Road bc;
    private Road ba;
    private Road cb;
    private Road bd;
    private Road dc;

    @BeforeEach
    void setUp() {
        graph = new RoadGraph();
        a = new Intersection("A", 0, 0);
        b = new Intersection("B", 100, 0);
        c = new Intersection("C", 100, 50);
        d = new Intersection("D", 200, 0);
        List.of(a, b, c, d).forEach(graph::addVertex);
        ab = new Road("AB", a, b, 100, 20, 1);
        bc = new Road("BC", b, c, 50, 5, 1);
        ba = new Road("BA", b, a, 100, 20, 1);
        cb = new Road("CB", c, b, 50, 5, 1);
        bd = new Road("BD", b, d, 100, 20, 1);
        dc = new Road("DC", d, c, 120, 20, 1);
        List.of(ab, bc, ba, cb, bd, dc).forEach(graph::addEdge);
    }

    @Test
    void changesRoadAndKeepsLeftoverDistance() {
        var car = car(new VehicleSpec(10, INSTANT_ACCELERATION, DECELERATION, LENGTH), SHORTEST, ab, bc);
        for (int i = 0; i < 9; i++) {
            car.update(1, FREE);
        }
        assertEquals(ab, car.currentRoad());
        assertEquals(90, car.offset(), DELTA);

        car.update(1.5, FREE);

        assertEquals(bc, car.currentRoad());
        assertEquals(5, car.offset(), DELTA);
        assertEquals(100, car.x(), DELTA);
        assertEquals(5, car.y(), DELTA);
    }

    @Test
    void accumulatesTripAndWaitTime() {
        var car = car(ab, bc);
        for (int i = 0; i < 10; i++) {
            car.update(0.1, 0);
        }
        assertEquals(1.0, car.tripSeconds(), DELTA);
        assertEquals(1.0, car.waitSeconds(), DELTA);

        for (int i = 0; i < 10; i++) {
            car.update(0.1, FREE);
        }
        assertEquals(2.0, car.tripSeconds(), DELTA);
        assertTrue(car.waitSeconds() < 2.0, "moving time is not waiting");
    }

    @Test
    void finishesAtDestination() {
        var car = car(ab, bc);
        for (int i = 0; i < 10_000 && !car.isFinished(); i++) {
            car.update(0.05, FREE);
        }

        assertTrue(car.isFinished());
        assertEquals(c, car.destination());
        assertEquals(c.x(), car.x(), DELTA);
        assertEquals(c.y(), car.y(), DELTA);
        assertEquals(0, car.speed());

        car.update(1, FREE);
        assertEquals(c.y(), car.y(), DELTA);
    }

    @Test
    void speedNeverExceedsCap() {
        var car = car(ab, bc);
        boolean reachedMaxSpeed = false;
        for (int i = 0; i < 10_000 && !car.isFinished(); i++) {
            car.update(0.05, FREE);
            double cap = Math.min(MAX_SPEED, car.currentRoad().speedLimit());
            assertTrue(car.speed() <= cap + DELTA, "speed " + car.speed() + " > cap " + cap);
            reachedMaxSpeed |= Math.abs(car.speed() - MAX_SPEED) < DELTA;
        }
        assertTrue(reachedMaxSpeed);
    }

    @Test
    void headingFollowsRoadDirection() {
        assertEquals(0, car(ab).headingRad(), DELTA);
        assertEquals(Math.PI, car(ba).headingRad(), DELTA);
        assertEquals(Math.PI / 2, car(bc).headingRad(), DELTA);
        assertEquals(-Math.PI / 2, car(cb).headingRad(), DELTA);
    }

    @Test
    void assignRouteResetsProgress() {
        var car = car(new VehicleSpec(MAX_SPEED, INSTANT_ACCELERATION, DECELERATION, LENGTH), SHORTEST, ab);
        for (int i = 0; i < 100 && !car.isFinished(); i++) {
            car.update(1, FREE);
        }
        assertTrue(car.isFinished());

        car.assignRoute(List.of(ab));

        assertFalse(car.isFinished());
        assertEquals(ab, car.currentRoad());
        assertEquals(0, car.offset());
        assertEquals(a.x(), car.x(), DELTA);
    }

    @Test
    void invalidRoutesAreRejected() {
        var car = new Car(SPEC, SHORTEST, a, c);

        assertThrows(IllegalStateException.class, car::currentRoad);
        assertThrows(IllegalArgumentException.class, () -> car.assignRoute(List.of()));
        assertThrows(IllegalArgumentException.class, () -> car.assignRoute(List.of(ab, ab)));
        assertThrows(IllegalArgumentException.class, () -> car.assignRoute(List.of(bc)));
        assertThrows(IllegalArgumentException.class, () -> car.assignRoute(List.of(ab)));
    }

    @Test
    void idsAreSequentialAndViewMatchesState() {
        var first = car(ab);
        var second = car(ab);

        assertTrue(first.id().matches("CAR-\\d+"), first.id());
        assertNotEquals(first.id(), second.id());

        first.update(1, FREE);
        VehicleView view = first.toView();
        assertEquals(first.id(), view.id());
        assertEquals(VehicleKind.CAR, view.kind());
        assertEquals(first.x(), view.x(), DELTA);
        assertEquals(first.speed(), view.speed(), DELTA);
        assertEquals(LENGTH, view.length(), DELTA);
        assertEquals("SHORTEST", view.strategyName());
    }

    @Test
    void stopsBeforeFreeDistance() {
        var car = car(ab);
        double obstacle = 30;
        for (int i = 0; i < 2_000; i++) {
            car.update(0.05, obstacle - car.offset());
            assertTrue(car.offset() <= obstacle + DELTA, "offset " + car.offset());
        }

        assertEquals(0, car.speed(), DELTA);
        assertEquals(obstacle, car.offset(), 0.5);
    }

    @Test
    void rearOffsetIsOffsetMinusLength() {
        var car = car(ab);
        assertEquals(-LENGTH, car.rearOffset(), DELTA);

        car.update(1, 40);

        assertEquals(car.offset() - LENGTH, car.rearOffset(), DELTA);
    }

    @Test
    void rerouteKeepsCurrentRoadAndProgress() {
        var detour = new FixedStrategy(List.of(bd, dc));
        var car = car(SPEC, detour, ab, bc);
        car.update(1, 30);
        double offset = car.offset();

        car.reroute(graph, b);

        assertEquals(1, detour.calls);
        assertEquals(List.of(ab, bd, dc), car.route());
        assertEquals(ab, car.currentRoad());
        assertEquals(offset, car.offset(), DELTA);
    }

    @Test
    void rerouteWithoutPathKeepsRoute() {
        var car = car(SPEC, new FixedStrategy(List.of()), ab, bc);

        car.reroute(graph, b);

        assertEquals(List.of(ab, bc), car.route());
    }

    @Test
    void rerouteMustStartAtEndOfCurrentRoad() {
        var car = car(ab, bc);

        assertThrows(IllegalArgumentException.class, () -> car.reroute(graph, a));
    }

    @Test
    void carHasNoEmergencyPriority() {
        assertFalse(car(ab).hasEmergencyPriority());
    }

    private static Car car(Road... route) {
        return car(SPEC, SHORTEST, route);
    }

    private static Car car(VehicleSpec spec, RouteStrategy strategy, Road... route) {
        var car = new Car(spec, strategy, route[0].from(), route[route.length - 1].to());
        car.assignRoute(List.of(route));
        return car;
    }
}
