package com.astra.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GraphTest {

    private record TestEdge(String from, String to, double weight) implements Edge<String> {
    }

    private Graph<String, TestEdge> graph;

    @BeforeEach
    void setUp() {
        graph = new Graph<>();
    }

    @Test
    void newGraphIsEmpty() {
        assertEquals(0, graph.vertexCount());
        assertEquals(0, graph.edgeCount());
        assertTrue(graph.vertices().isEmpty());
        assertTrue(graph.edges().isEmpty());
    }

    @Test
    void addVertexIsIdempotent() {
        assertTrue(graph.addVertex("A"));
        assertFalse(graph.addVertex("A"));

        assertEquals(1, graph.vertexCount());
        assertTrue(graph.containsVertex("A"));
        assertFalse(graph.containsVertex("B"));
        assertFalse(graph.containsVertex(null));
    }

    @Test
    void addEdgeIsDirected() {
        graph.addVertex("A");
        graph.addVertex("B");
        var ab = new TestEdge("A", "B", 2.5);

        graph.addEdge(ab);

        assertEquals(List.of(ab), graph.outgoing("A"));
        assertTrue(graph.outgoing("B").isEmpty());
        assertEquals(1, graph.edgeCount());
        assertEquals(2.5, graph.outgoing("A").get(0).weight());
    }

    @Test
    void verticesAndEdgesListEverything() {
        graph.addVertex("A");
        graph.addVertex("B");
        graph.addVertex("C");
        var ab = new TestEdge("A", "B", 1);
        var ac = new TestEdge("A", "C", 2);
        var cb = new TestEdge("C", "B", 3);
        graph.addEdge(ab);
        graph.addEdge(ac);
        graph.addEdge(cb);

        assertEquals(Set.of("A", "B", "C"), graph.vertices());
        assertEquals(Set.of(ab, ac, cb), Set.copyOf(graph.edges()));
        assertEquals(3, graph.edgeCount());
        assertEquals(2, graph.outgoing("A").size());
    }

    @Test
    void addEdgeWithUnknownEndpointThrows() {
        graph.addVertex("A");

        assertThrows(IllegalArgumentException.class, () -> graph.addEdge(new TestEdge("A", "X", 1)));
        assertThrows(IllegalArgumentException.class, () -> graph.addEdge(new TestEdge("X", "A", 1)));
        assertEquals(0, graph.edgeCount());
    }

    @Test
    void outgoingOfUnknownVertexThrows() {
        assertThrows(IllegalArgumentException.class, () -> graph.outgoing("X"));
    }

    @Test
    void returnedCollectionsAreUnmodifiable() {
        graph.addVertex("A");
        graph.addVertex("B");
        var ab = new TestEdge("A", "B", 1);
        graph.addEdge(ab);

        assertThrows(UnsupportedOperationException.class, () -> graph.outgoing("A").add(ab));
        assertThrows(UnsupportedOperationException.class, () -> graph.vertices().add("C"));
        assertThrows(UnsupportedOperationException.class, () -> graph.edges().clear());
    }

    @Test
    void nullArgumentsAreRejected() {
        assertThrows(NullPointerException.class, () -> graph.addVertex(null));
        assertThrows(NullPointerException.class, () -> graph.addEdge(null));
    }
}
