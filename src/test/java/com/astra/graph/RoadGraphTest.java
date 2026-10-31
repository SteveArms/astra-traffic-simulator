package com.astra.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RoadGraphTest {

    private static final double DELTA = 1e-9;

    private RoadGraph graph;
    private Intersection a;
    private Intersection b;

    @BeforeEach
    void setUp() {
        graph = new RoadGraph();
        a = new Intersection("A", 0, 0);
        b = new Intersection("B", 30, 40);
        graph.addVertex(a);
        graph.addVertex(b);
    }

    @Test
    void addTwoWayRoadCreatesBothDirections() {
        List<Road> roads = graph.addTwoWayRoad("R1", a, b, 10, 2);

        Road forward = roads.get(0);
        Road backward = roads.get(1);
        assertEquals("R1-F", forward.id());
        assertEquals(a, forward.from());
        assertEquals(b, forward.to());
        assertEquals("R1-B", backward.id());
        assertEquals(b, backward.from());
        assertEquals(a, backward.to());
        assertEquals(List.of(forward), graph.outgoing(a));
        assertEquals(List.of(backward), graph.outgoing(b));
        assertEquals(2, graph.edgeCount());
    }

    @Test
    void twoWayRoadLengthIsEuclideanDistance() {
        Road forward = graph.addTwoWayRoad("R1", a, b, 10, 1).get(0);

        assertEquals(50, forward.length(), DELTA);
        assertEquals(50, forward.weight(), DELTA);
        assertEquals(5, forward.travelTimeSeconds(), DELTA);
    }

    @Test
    void twoWayRoadWithUnknownIntersectionAddsNothing() {
        var outside = new Intersection("Z", 100, 100);

        assertThrows(IllegalArgumentException.class, () -> graph.addTwoWayRoad("R1", a, outside, 10, 1));
        assertEquals(0, graph.edgeCount());
    }

    @Test
    void findIntersectionById() {
        assertEquals(Optional.of(a), graph.findIntersection("A"));
        assertEquals(Optional.empty(), graph.findIntersection("missing"));
        assertEquals(Optional.empty(), graph.findIntersection(null));
    }

    @Test
    void duplicateIntersectionIdIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> graph.addVertex(new Intersection("A", 5, 5)));
        assertEquals(Optional.of(a), graph.findIntersection("A"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void intersectionIdMustNotBeBlank(String id) {
        assertThrows(IllegalArgumentException.class, () -> new Intersection(id, 0, 0));
    }

    @Test
    void intersectionIdMustNotBeNull() {
        assertThrows(IllegalArgumentException.class, () -> new Intersection(null, 0, 0));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, -1, Double.NaN})
    void roadLengthMustBePositive(double length) {
        assertThrows(IllegalArgumentException.class, () -> new Road("R", a, b, length, 10, 1));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, -5, Double.NaN})
    void roadSpeedLimitMustBePositive(double speedLimit) {
        assertThrows(IllegalArgumentException.class, () -> new Road("R", a, b, 10, speedLimit, 1));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void roadNeedsAtLeastOneLane(int lanes) {
        assertThrows(IllegalArgumentException.class, () -> new Road("R", a, b, 10, 10, lanes));
    }

    @Test
    void roadCannotBeALoop() {
        assertThrows(IllegalArgumentException.class, () -> new Road("R", a, a, 10, 10, 1));
    }

    @Test
    void validRoadExposesTravelTime() {
        var road = new Road("R", a, b, 100, 20, 3);

        assertEquals(5, road.travelTimeSeconds(), DELTA);
        assertTrue(road.weight() > 0);
    }
}
