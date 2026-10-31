package com.astra.graph;

public record Intersection(String id, double x, double y) {

    public Intersection {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Intersection id must not be null or blank");
        }
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            throw new IllegalArgumentException("Intersection " + id + " has non-finite coordinates");
        }
    }

    public double distanceTo(Intersection other) {
        return Math.hypot(other.x - x, other.y - y);
    }
}
