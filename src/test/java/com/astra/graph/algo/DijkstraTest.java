package com.astra.graph.algo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.astra.graph.Edge;
import com.astra.graph.Graph;
import com.astra.graph.Intersection;
import com.astra.graph.Road;
import com.astra.graph.RoadGraph;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DijkstraTest {

    private static final double DELTA = 1e-9;

    private record TestEdge(String from, String to, double weight) implements Edge<String> {
    }

    private Graph<String, TestEdge> graph;
    private TestEdge ab;
    private TestEdge ac;
    private TestEdge cb;
    private TestEdge bd;
    private TestEdge cd;
    private TestEdge de;
    private TestEdge ce;

    @BeforeEach
    void setUp() {
        graph = new Graph<>();
        List.of("A", "B", "C", "D", "E", "F").forEach(graph::addVertex);
        ab = edge("A", "B", 4);
        ac = edge("A", "C", 2);
        cb = edge("C", "B", 1);
        bd = edge("B", "D", 5);
        cd = edge("C", "D", 8);
        de = edge("D", "E", 3);
        ce = edge("C", "E", 10);
    }

    private TestEdge edge(String from, String to, double weight) {
        var e = new TestEdge(from, to, weight);
        graph.addEdge(e);
        return e;
    }

    @Test
    void findsKnownOptimalPath() {
        List<TestEdge> path = new Dijkstra<String, TestEdge>().shortestPath(graph, "A", "E");

        assertEquals(List.of(ac, cb, bd, de), path);
        assertEquals(11, Dijkstra.totalWeight(path, TestEdge::weight), DELTA);
    }

    @Test
    void unreachableNodeGivesEmptyPath() {
        var dijkstra = new Dijkstra<String, TestEdge>();

        assertTrue(dijkstra.shortestPath(graph, "A", "F").isEmpty());
        assertTrue(dijkstra.shortestPath(graph, "E", "A").isEmpty());
    }

    @Test
    void sameSourceAndDestinationGivesEmptyPath() {
        assertTrue(new Dijkstra<String, TestEdge>().shortestPath(graph, "A", "A").isEmpty());
    }

    @Test
    void unknownVertexThrows() {
        var dijkstra = new Dijkstra<String, TestEdge>();

        assertThrows(IllegalArgumentException.class, () -> dijkstra.shortestPath(graph, "X", "A"));
        assertThrows(IllegalArgumentException.class, () -> dijkstra.shortestPath(graph, "A", "X"));
    }

    @Test
    void negativeWeightThrows() {
        edge("A", "F", -1);

        assertThrows(IllegalArgumentException.class,
                () -> new Dijkstra<String, TestEdge>().shortestPath(graph, "A", "F"));
    }

    @Test
    void customWeightChangesRoute() {
        // Pesar cada arista con 1 convierte el problema en "menos saltos".
        List<TestEdge> path = new Dijkstra<String, TestEdge>().shortestPath(graph, "A", "E", e -> 1);

        assertEquals(List.of(ac, ce), path);
    }

    @Test
    void roadGraphRouteDependsOnDistanceOrTime() {
        var roads = new RoadGraph();
        var a = new Intersection("A", 0, 0);
        var b = new Intersection("B", 100, 0);
        var c = new Intersection("C", 50, 50);
        List.of(a, b, c).forEach(roads::addVertex);
        Road direct = roads.addTwoWayRoad("AB", a, b, 5, 1).get(0);
        Road toC = roads.addTwoWayRoad("AC", a, c, 20, 2).get(0);
        Road cToB = roads.addTwoWayRoad("CB", c, b, 20, 2).get(0);
        var dijkstra = new Dijkstra<Intersection, Road>();

        assertEquals(List.of(direct), dijkstra.shortestPath(roads, a, b));

        List<Road> fastest = dijkstra.shortestPath(roads, a, b, Road::travelTimeSeconds);
        assertEquals(List.of(toC, cToB), fastest);
        assertEquals(2 * Math.hypot(50, 50) / 20, Dijkstra.totalWeight(fastest, Road::travelTimeSeconds), DELTA);
    }
}
