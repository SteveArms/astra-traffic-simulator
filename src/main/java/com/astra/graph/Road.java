package com.astra.graph;

import java.util.Objects;

/** Calle dirigida. {@code speedLimit} usa la misma unidad de longitud por segundo. */
public record Road(String id, Intersection from, Intersection to, double length, double speedLimit, int lanes)
        implements Edge<Intersection> {

    public Road {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Road id must not be null or blank");
        }
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.equals(to)) {
            throw new IllegalArgumentException("Road " + id + " must connect two different intersections");
        }
        if (!(length > 0) || !Double.isFinite(length)) {
            throw new IllegalArgumentException("Road " + id + " length must be > 0: " + length);
        }
        if (!(speedLimit > 0) || !Double.isFinite(speedLimit)) {
            throw new IllegalArgumentException("Road " + id + " speedLimit must be > 0: " + speedLimit);
        }
        if (lanes < 1) {
            throw new IllegalArgumentException("Road " + id + " lanes must be >= 1: " + lanes);
        }
    }

    @Override
    public double weight() {
        return length;
    }

    public double travelTimeSeconds() {
        return length / speedLimit;
    }
}
