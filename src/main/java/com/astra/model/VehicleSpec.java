package com.astra.model;

/** Capacidades físicas de un tipo de vehículo: px/s, px/s² y px. */
public record VehicleSpec(double maxSpeed, double acceleration, double deceleration, double length) {

    public VehicleSpec {
        requirePositive("maxSpeed", maxSpeed);
        requirePositive("acceleration", acceleration);
        requirePositive("deceleration", deceleration);
        requirePositive("length", length);
    }

    private static void requirePositive(String name, double value) {
        if (!(value > 0) || !Double.isFinite(value)) {
            throw new IllegalArgumentException("VehicleSpec " + name + " must be > 0: " + value);
        }
    }
}
