package com.astra.routing;

import com.astra.graph.Road;
import java.util.Map;
import java.util.Objects;

/** Ocupación de cada vía entre 0 (libre) y 1 (llena). Inmutable: se puede leer desde cualquier hilo. */
public record RoadOccupancy(Map<Road, Double> ratios) {

    public static final RoadOccupancy EMPTY = new RoadOccupancy(Map.of());

    public RoadOccupancy {
        Objects.requireNonNull(ratios, "ratios");
        ratios = Map.copyOf(ratios);
        ratios.forEach((road, ratio) -> {
            if (!(ratio >= 0 && ratio <= 1)) {
                throw new IllegalArgumentException("Occupancy of " + road.id() + " must be in [0, 1]: " + ratio);
            }
        });
    }

    /** @return 0 si la vía no tiene ocupación registrada */
    public double of(Road road) {
        return ratios.getOrDefault(road, 0.0);
    }
}
