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
import java.util.function.ToDoubleBiFunction;
import java.util.function.ToDoubleFunction;

/**
 * Camino mínimo A* guiado por un heurístico {@code h(v, dst)}. Sin estado compartido: thread-safe.
 *
 * <p>El heurístico debe ser <b>admisible</b>: nunca sobreestimar el costo real restante hasta el destino.
 * Solo así el camino devuelto es óptimo. Con {@code h = 0} el algoritmo equivale a Dijkstra.
 */
public final class AStar<V, E extends Edge<V>> {

    /** @param expanded nodos sacados de la cola y expandidos (sin contar entradas obsoletas) */
    public record Result<E>(List<E> path, int expanded) {
        public Result {
            path = List.copyOf(path);
        }
    }

    private record Entry<V>(V node, double g, double f) {
    }

    public List<E> shortestPath(Graph<V, E> g, V src, V dst, ToDoubleBiFunction<? super V, ? super V> heuristic) {
        return shortestPath(g, src, dst, Edge::weight, heuristic);
    }

    /** @return vacía si {@code src == dst} o no hay camino */
    public List<E> shortestPath(Graph<V, E> g, V src, V dst, ToDoubleFunction<? super E> weight,
            ToDoubleBiFunction<? super V, ? super V> heuristic) {
        return search(g, src, dst, weight, heuristic).path();
    }

    public Result<E> search(Graph<V, E> g, V src, V dst, ToDoubleBiFunction<? super V, ? super V> heuristic) {
        return search(g, src, dst, Edge::weight, heuristic);
    }

    /**
     * @throws IllegalArgumentException si {@code src} o {@code dst} no están en el grafo, o algún peso
     *                                  recorrido es negativo o NaN
     */
    public Result<E> search(Graph<V, E> g, V src, V dst, ToDoubleFunction<? super E> weight,
            ToDoubleBiFunction<? super V, ? super V> heuristic) {
        Objects.requireNonNull(g, "graph");
        Objects.requireNonNull(weight, "weight");
        Objects.requireNonNull(heuristic, "heuristic");
        if (!g.containsVertex(src) || !g.containsVertex(dst)) {
            throw new IllegalArgumentException("Unknown vertex: " + (g.containsVertex(src) ? dst : src));
        }
        if (src.equals(dst)) {
            return new Result<>(List.of(), 0);
        }

        Map<V, Double> bestG = new HashMap<>();
        Map<V, E> previous = new HashMap<>();
        var queue = new PriorityQueue<Entry<V>>(Comparator.comparingDouble(Entry::f));
        bestG.put(src, 0.0);
        queue.add(new Entry<>(src, 0.0, heuristic.applyAsDouble(src, dst)));
        int expanded = 0;

        while (!queue.isEmpty()) {
            Entry<V> current = queue.poll();
            // Lazy deletion: se ignoran entradas superadas por un g mejor encontrado después.
            if (current.g() > bestG.get(current.node())) {
                continue;
            }
            if (current.node().equals(dst)) {
                break;
            }
            expanded++;
            for (E edge : g.outgoing(current.node())) {
                double w = weight.applyAsDouble(edge);
                if (!(w >= 0)) {
                    throw new IllegalArgumentException("Negative or NaN weight " + w + " on edge " + edge);
                }
                double candidate = current.g() + w;
                if (candidate < bestG.getOrDefault(edge.to(), Double.POSITIVE_INFINITY)) {
                    bestG.put(edge.to(), candidate);
                    previous.put(edge.to(), edge);
                    queue.add(new Entry<>(edge.to(), candidate, candidate + heuristic.applyAsDouble(edge.to(), dst)));
                }
            }
        }

        if (!previous.containsKey(dst)) {
            return new Result<>(List.of(), expanded);
        }
        var path = new ArrayList<E>();
        for (V node = dst; !node.equals(src); node = previous.get(node).from()) {
            path.add(previous.get(node));
        }
        Collections.reverse(path);
        return new Result<>(path, expanded);
    }
}
