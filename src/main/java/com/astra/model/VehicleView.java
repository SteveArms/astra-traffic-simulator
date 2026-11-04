package com.astra.model;

import java.util.Objects;

/** Estado visible de un vehículo en un instante; {@code x, y} es el parachoques delantero. */
public record VehicleView(String id, VehicleKind kind, double x, double y, double headingRad, double speed,
        double length, String strategyName) {

    public VehicleView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(strategyName, "strategyName");
    }
}
