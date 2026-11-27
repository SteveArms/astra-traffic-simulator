package com.astra.traffic;

import java.time.Duration;
import java.util.Objects;

/**
 * Amarillo para {@code active} y rojo para el otro eje; dura {@code lights.yellowSeconds} y sigue el todo rojo
 * antes del verde del eje contrario.
 */
public record YellowState(Axis active) implements LightState {

    public YellowState {
        Objects.requireNonNull(active, "active");
    }

    @Override
    public LightColor colorFor(Axis axis) {
        return axis == active ? LightColor.YELLOW : LightColor.RED;
    }

    @Override
    public Duration duration(LightTimings timings) {
        return timings.yellowSeconds();
    }

    @Override
    public LightState next() {
        return new RedState(active.opposite());
    }

    @Override
    public String name() {
        return "YELLOW(" + active + ")";
    }
}
