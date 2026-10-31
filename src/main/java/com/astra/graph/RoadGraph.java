package com.astra.graph;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Grafo vial con índice de intersecciones por id. Mismas reglas de concurrencia que {@link Graph}. */
public final class RoadGraph extends Graph<Intersection, Road> {

    private static final String FORWARD_SUFFIX = "-F";
    private static final String BACKWARD_SUFFIX = "-B";

    private final ConcurrentHashMap<String, Intersection> byId = new ConcurrentHashMap<>();

    /** @throws IllegalArgumentException si ya existe otra intersección con el mismo id */
    @Override
    public boolean addVertex(Intersection intersection) {
        Objects.requireNonNull(intersection, "intersection");
        Intersection existing = byId.putIfAbsent(intersection.id(), intersection);
        if (existing != null && !existing.equals(intersection)) {
            throw new IllegalArgumentException("Duplicate intersection id: " + intersection.id());
        }
        return super.addVertex(intersection);
    }

    public Optional<Intersection> findIntersection(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(byId.get(id));
    }

    /** Crea {@code <baseId>-F} (a→b) y {@code <baseId>-B} (b→a) con la distancia euclidiana como longitud. */
    public List<Road> addTwoWayRoad(String baseId, Intersection a, Intersection b, double speedLimit, int lanes) {
        Objects.requireNonNull(a, "a");
        Objects.requireNonNull(b, "b");
        double length = a.distanceTo(b);
        var forward = new Road(baseId + FORWARD_SUFFIX, a, b, length, speedLimit, lanes);
        var backward = new Road(baseId + BACKWARD_SUFFIX, b, a, length, speedLimit, lanes);
        // Validar ambos extremos antes de insertar para no dejar una calle de un solo sentido.
        if (!containsVertex(a) || !containsVertex(b)) {
            throw new IllegalArgumentException("Both intersections must be in the graph: " + a.id() + ", " + b.id());
        }
        addEdge(forward);
        addEdge(backward);
        return List.of(forward, backward);
    }
}
