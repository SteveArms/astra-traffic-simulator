package com.astra.traffic;

import java.time.Duration;
import java.util.Objects;

/**
 * Verde forzado para el eje por donde llega un vehículo de emergencia. Lo libera {@link TrafficLight#release};
 * si nadie lo libera, sale solo tras {@link LightTimings#emergencyMaxSeconds()} como límite de seguridad.
 */
public record EmergencyState(Axis axis) implements LightState {

    public EmergencyState {
        Objects.requireNonNull(axis, "axis");
    }

    @Override
    public LightColor colorFor(Axis other) {
        return other == axis ? LightColor.GREEN : LightColor.RED;
    }

    @Override
    public Duration duration(LightTimings timings) {
        return timings.emergencyMaxSeconds();
    }

    @Override
    public LightState next() {
        return new YellowState(axis);
    }

    @Override
    public String name() {
        return "EMERGENCY-" + axis;
    }
}
