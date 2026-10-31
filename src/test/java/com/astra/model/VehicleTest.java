package com.astra.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.astra.graph.Intersection;
import com.astra.graph.Road;
import com.astra.graph.RoadGraph;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VehicleTest {

    private static final double DELTA = 1e-9;
    private static final double MAX_SPEED = 14;
    private static final double ACCELERATION = 3;
    // La velocidad llega al tope en el primer update, así los cálculos son exactos.
    private static final double INSTANT_ACCELERATION = 1_000;

    private Intersection a;
    private Intersection b;
    private Intersection c;
    private Road ab;
    private Road bc;
    private Road ba;
    private Road cb;

    @BeforeEach
    void setUp() {
        var graph = new RoadGraph();
        a = new Intersection("A", 0, 0);
        b = new Intersection("B", 100, 0);
        c = new Intersection("C", 100, 50);
        List.of(a, b, c).forEach(graph::addVertex);
        ab = new Road("AB", a, b, 100, 20, 1);
        bc = new Road("BC", b, c, 50, 5, 1);
        ba = new Road("BA", b, a, 100, 20, 1);
        cb = new Road("CB", c, b, 50, 5, 1);
        List.of(ab, bc, ba, cb).forEach(graph::addEdge);
    }

    @Test
    void changesRoadAndKeepsLeftoverDistance() {
        var car = new Car(List.of(ab, bc), 10, INSTANT_ACCELERATION);
        for (int i = 0; i < 9; i++) {
            car.update(1);
        }
        assertEquals(ab, car.currentRoad());
        assertEquals(90, car.offset(), DELTA);

        car.update(1.5);

        assertEquals(bc, car.currentRoad());
        assertEquals(5, car.offset(), DELTA);
        assertEquals(100, car.x(), DELTA);
        assertEquals(5, car.y(), DELTA);
    }

    @Test
    void finishesAtDestination() {
        var car = new Car(List.of(ab, bc), MAX_SPEED, ACCELERATION);
        for (int i = 0; i < 10_000 && !car.isFinished(); i++) {
            car.update(0.05);
        }

        assertTrue(car.isFinished());
        assertEquals(c, car.destination());
        assertEquals(c.x(), car.x(), DELTA);
        assertEquals(c.y(), car.y(), DELTA);
        assertEquals(0, car.speed());

        car.update(1);
        assertEquals(c.y(), car.y(), DELTA);
    }

    @Test
    void speedNeverExceedsCap() {
        var car = new Car(List.of(ab, bc), MAX_SPEED, ACCELERATION);
        boolean reachedMaxSpeed = false;
        for (int i = 0; i < 10_000 && !car.isFinished(); i++) {
            car.update(0.05);
            double cap = Math.min(MAX_SPEED, car.currentRoad().speedLimit());
            assertTrue(car.speed() <= cap + DELTA, "speed " + car.speed() + " > cap " + cap);
            reachedMaxSpeed |= Math.abs(car.speed() - MAX_SPEED) < DELTA;
        }
        assertTrue(reachedMaxSpeed);
    }

    @Test
    void headingFollowsRoadDirection() {
        assertEquals(0, new Car(List.of(ab), MAX_SPEED, ACCELERATION).headingRad(), DELTA);
        assertEquals(Math.PI, new Car(List.of(ba), MAX_SPEED, ACCELERATION).headingRad(), DELTA);
        assertEquals(Math.PI / 2, new Car(List.of(bc), MAX_SPEED, ACCELERATION).headingRad(), DELTA);
        assertEquals(-Math.PI / 2, new Car(List.of(cb), MAX_SPEED, ACCELERATION).headingRad(), DELTA);
    }

    @Test
    void assignRouteResetsProgress() {
        var car = new Car(List.of(ab), MAX_SPEED, INSTANT_ACCELERATION);
        for (int i = 0; i < 100 && !car.isFinished(); i++) {
            car.update(1);
        }
        assertTrue(car.isFinished());

        car.assignRoute(List.of(ba));

        assertFalse(car.isFinished());
        assertEquals(ba, car.currentRoad());
        assertEquals(0, car.offset());
        assertEquals(b.x(), car.x(), DELTA);
    }

    @Test
    void invalidRoutesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Car(List.of(), MAX_SPEED, ACCELERATION));
        assertThrows(IllegalArgumentException.class, () -> new Car(List.of(ab, ab), MAX_SPEED, ACCELERATION));
        var car = new Car(List.of(ab), MAX_SPEED, ACCELERATION);
        assertThrows(IllegalArgumentException.class, () -> car.assignRoute(List.of(bc, ab)));
    }

    @Test
    void carIdsAreSequentialAndViewMatchesState() {
        var first = new Car(List.of(ab), MAX_SPEED, ACCELERATION);
        var second = new Car(List.of(ab), MAX_SPEED, ACCELERATION);

        assertTrue(first.id().matches("CAR-\\d+"), first.id());
        assertNotEquals(first.id(), second.id());

        first.update(1);
        VehicleView view = first.toView();
        assertEquals(first.id(), view.id());
        assertEquals(VehicleKind.CAR, view.kind());
        assertEquals(first.x(), view.x(), DELTA);
        assertEquals(first.speed(), view.speed(), DELTA);
    }
}
