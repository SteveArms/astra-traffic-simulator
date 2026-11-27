package com.astra.traffic;

import java.time.Duration;
import java.util.Objects;

/**
 * Todo rojo entre el amarillo de un eje y el verde de {@code nextGreen}: deja terminar de cruzar a quien ya entró.
 * Dura {@code lights.allRedSeconds}.
 */
public record RedState(Axis nextGreen) implements LightState {

    public RedState {
        Objects.requireNonNull(nextGreen, "nextGreen");
    }

    @Override
    public LightColor colorFor(Axis axis) {
        return LightColor.RED;
    }

    @Override
    public Duration duration(LightTimings timings) {
        return timings.allRedSeconds();
    }

    @Override
    public LightState next() {
        return new GreenState(nextGreen);
    }

    @Override
    public String name() {
        return "RED(next " + nextGreen + ")";
    }
}
