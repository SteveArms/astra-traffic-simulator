package com.astra.graph;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Grafo dirigido genérico. Se construye antes de la simulación; luego se lee desde cualquier hilo. */
public class Graph<V, E extends Edge<V>> {

    private final ConcurrentHashMap<V, CopyOnWriteArrayList<E>> adjacency = new ConcurrentHashMap<>();

    public boolean addVertex(V vertex) {
        Objects.requireNonNull(vertex, "vertex");
        return adjacency.putIfAbsent(vertex, new CopyOnWriteArrayList<>()) == null;
    }

    public void addEdge(E edge) {
        Objects.requireNonNull(edge, "edge");
        requireVertex(edge.to(), "to");
        adjacency.get(requireVertex(edge.from(), "from")).add(edge);
    }

    public boolean containsVertex(V vertex) {
        return vertex != null && adjacency.containsKey(vertex);
    }

    public List<E> outgoing(V vertex) {
        return Collections.unmodifiableList(adjacency.get(requireVertex(vertex, "vertex")));
    }

    public Set<V> vertices() {
        return Set.copyOf(adjacency.keySet());
    }

    public List<E> edges() {
        return adjacency.values().stream()
                .flatMap(List::stream)
                .toList();
    }

    public int vertexCount() {
        return adjacency.size();
    }

    public int edgeCount() {
        return adjacency.values().stream()
                .mapToInt(List::size)
                .sum();
    }

    private V requireVertex(V vertex, String role) {
        if (!containsVertex(vertex)) {
            throw new IllegalArgumentException("Unknown " + role + " vertex: " + vertex);
        }
        return vertex;
    }
}
