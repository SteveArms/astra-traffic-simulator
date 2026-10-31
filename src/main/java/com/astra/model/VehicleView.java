package com.astra.model;

import java.util.Objects;

/** Estado visible de un vehículo en un instante; parte de {@code WorldSnapshot}. */
public record VehicleView(String id, VehicleKind kind, double x, double y, double headingRad, double speed) {

    public VehicleView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
    }
}
