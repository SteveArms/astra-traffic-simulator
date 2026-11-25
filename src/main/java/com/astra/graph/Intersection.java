package com.astra.graph;

import java.util.Objects;

/**
 * Nodo del mapa.
 *
 * @param capacity plazas de una {@link NodeKind#PARKING} (>= 1); 0 para los demás tipos
 */
public record Intersection(String id, double x, double y, NodeKind kind, int capacity) {

    public Intersection {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Intersection id must not be null or blank");
        }
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            throw new IllegalArgumentException("Intersection " + id + " has non-finite coordinates");
        }
        Objects.requireNonNull(kind, "kind");
        if (kind == NodeKind.PARKING && capacity < 1) {
            throw new IllegalArgumentException("Parking " + id + " needs capacity >= 1: " + capacity);
        }
        if (kind != NodeKind.PARKING && capacity != 0) {
            throw new IllegalArgumentException(kind + " " + id + " cannot have capacity: " + capacity);
        }
    }

    /** Un cruce común, sin capacidad. */
    public Intersection(String id, double x, double y) {
        this(id, x, y, NodeKind.INTERSECTION, 0);
    }

    public boolean isGateway() {
        return kind == NodeKind.GATEWAY;
    }

    public boolean isParking() {
        return kind == NodeKind.PARKING;
    }

    public double distanceTo(Intersection other) {
        return Math.hypot(other.x - x, other.y - y);
    }
}
