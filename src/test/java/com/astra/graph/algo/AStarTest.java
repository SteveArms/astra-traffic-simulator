package com.astra.graph.algo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.astra.graph.Edge;
import com.astra.graph.Graph;
import com.astra.graph.Heuristics;
import com.astra.graph.Intersection;
import com.astra.graph.Road;
import com.astra.graph.RoadGraph;
import com.astra.util.MapLoader;
import java.util.List;
import java.util.function.ToDoubleBiFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AStarTest {

    private static final double DELTA = 1e-9;

    private record TestEdge(String from, String to, double weight) implements Edge<String> {
    }

    private static <V> ToDoubleBiFunction<V, V> zero() {
        return (a, b) -> 0;
    }

    private Graph<String, TestEdge> graph;
    private TestEdge ac;
    private TestEdge cb;
    private TestEdge bd;
    private TestEdge de;
    private TestEdge ce;

    @BeforeEach
    void setUp() {
        graph = new Graph<>();
        List.of("A", "B", "C", "D", "E", "F").forEach(graph::addVertex);
        edge("A", "B", 4);
        ac = edge("A", "C", 2);
        cb = edge("C", "B", 1);
        bd = edge("B", "D", 5);
        edge("C", "D", 8);
        de = edge("D", "E", 3);
        ce = edge("C", "E", 10);
    }

    private TestEdge edge(String from, String to, double weight) {
        var e = new TestEdge(from, to, weight);
        graph.addEdge(e);
        return e;
    }

    @Test
    void matchesDijkstraCostForAllPairsOnCity3x3() {
        RoadGraph roads = MapLoader.load("maps/city-3x3.json").graph();
        var aStar = new AStar<Intersection, Road>();
        var dijkstra = new Dijkstra<Intersection, Road>();
        int pairs = 0;

        for (Intersection from : roads.vertices()) {
            for (Intersection to : roads.vertices()) {
                double expected = Dijkstra.totalWeight(dijkstra.shortestPath(roads, from, to), Road::weight);
                List<Road> path = aStar.shortestPath(roads, from, to, Heuristics.euclidean());
                assertEquals(expected, Dijkstra.totalWeight(path, Road::weight), DELTA, from.id() + " -> " + to.id());
                pairs++;
            }
        }
        assertEquals(81, pairs);
    }

    @Test
    void euclideanExpandsNoMoreNodesThanZeroHeuristic() {
        RoadGraph roads = MapLoader.load("maps/city-3x3.json").graph();
        var aStar = new AStar<Intersection, Road>();

        for (Intersection from : roads.vertices()) {
            for (Intersection to : roads.vertices()) {
                int informed = aStar.search(roads, from, to, Heuristics.euclidean()).expanded();
                int blind = aStar.search(roads, from, to, zero()).expanded();
                assertTrue(informed <= blind, from.id() + " -> " + to.id() + ": " + informed + " > " + blind);
            }
        }
    }

    @Test
    void worksWithGenericGraphAndZeroHeuristic() {
        List<TestEdge> path = new AStar<String, TestEdge>().shortestPath(graph, "A", "E", zero());

        assertEquals(List.of(ac, cb, bd, de), path);
        assertEquals(11, Dijkstra.totalWeight(path, TestEdge::weight), DELTA);
    }

    @Test
    void customWeightChangesRoute() {
        List<TestEdge> path = new AStar<String, TestEdge>().shortestPath(graph, "A", "E", e -> 1, zero());

        assertEquals(List.of(ac, ce), path);
    }

    @Test
    void unreachableNodeGivesEmptyPath() {
        var aStar = new AStar<String, TestEdge>();

        assertTrue(aStar.shortestPath(graph, "A", "F", zero()).isEmpty());
        assertTrue(aStar.shortestPath(graph, "E", "A", zero()).isEmpty());
    }

    @Test
    void sameSourceAndDestinationGivesEmptyPath() {
        AStar.Result<TestEdge> result = new AStar<String, TestEdge>().search(graph, "A", "A", zero());

        assertTrue(result.path().isEmpty());
        assertEquals(0, result.expanded());
    }

    @Test
    void unknownVertexThrows() {
        var aStar = new AStar<String, TestEdge>();

        assertThrows(IllegalArgumentException.class, () -> aStar.shortestPath(graph, "X", "A", zero()));
        assertThrows(IllegalArgumentException.class, () -> aStar.shortestPath(graph, "A", "X", zero()));
    }

    @Test
    void negativeWeightThrows() {
        edge("A", "F", -1);

        assertThrows(IllegalArgumentException.class,
                () -> new AStar<String, TestEdge>().shortestPath(graph, "A", "F", zero()));
    }
}
