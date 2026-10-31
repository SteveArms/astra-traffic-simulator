package com.astra.graph.algo;

import com.astra.graph.Edge;
import com.astra.graph.Graph;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.function.ToDoubleFunction;

/**
 * Camino mínimo de Dijkstra en O((V + E) log V). No guarda estado entre llamadas, así que una
 * instancia es thread-safe y puede usarse desde varios hilos a la vez.
 */
public final class Dijkstra<V, E extends Edge<V>> {

    private record NodeDist<V>(V node, double dist) {
    }

    public List<E> shortestPath(Graph<V, E> g, V src, V dst) {
        return shortestPath(g, src, dst, Edge::weight);
    }

    /**
     * @return aristas del camino en orden; vacía si {@code src == dst} o no hay camino
     * @throws IllegalArgumentException si {@code src} o {@code dst} no están en el grafo, o si
     *                                  se recorre una arista con peso negativo o NaN
     */
    public List<E> shortestPath(Graph<V, E> g, V src, V dst, ToDoubleFunction<? super E> weight) {
        Objects.requireNonNull(g, "graph");
        Objects.requireNonNull(weight, "weight");
        if (!g.containsVertex(src) || !g.containsVertex(dst)) {
            throw new IllegalArgumentException("Unknown vertex: " + (g.containsVertex(src) ? dst : src));
        }
        if (src.equals(dst)) {
            return List.of();
        }

        Map<V, Double> dist = new HashMap<>();
        Map<V, E> previous = new HashMap<>();
        var queue = new PriorityQueue<NodeDist<V>>(Comparator.comparingDouble(NodeDist::dist));
        dist.put(src, 0.0);
        queue.add(new NodeDist<>(src, 0.0));

        while (!queue.isEmpty()) {
            NodeDist<V> current = queue.poll();
            if (current.dist() > dist.get(current.node())) {
                continue;
            }
            if (current.node().equals(dst)) {
                break;
            }
            for (E edge : g.outgoing(current.node())) {
                double w = weight.applyAsDouble(edge);
                if (!(w >= 0)) {
                    throw new IllegalArgumentException("Negative or NaN weight " + w + " on edge " + edge);
                }
                double candidate = current.dist() + w;
                if (candidate < dist.getOrDefault(edge.to(), Double.POSITIVE_INFINITY)) {
                    dist.put(edge.to(), candidate);
                    previous.put(edge.to(), edge);
                    queue.add(new NodeDist<>(edge.to(), candidate));
                }
            }
        }

        if (!previous.containsKey(dst)) {
            return List.of();
        }
        var path = new ArrayList<E>();
        for (V node = dst; !node.equals(src); node = previous.get(node).from()) {
            path.add(previous.get(node));
        }
        Collections.reverse(path);
        return List.copyOf(path);
    }

    public static <E> double totalWeight(List<E> path, ToDoubleFunction<? super E> weight) {
        return path.stream().mapToDouble(weight).sum();
    }
}
