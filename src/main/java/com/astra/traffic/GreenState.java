package com.astra.traffic;

import java.time.Duration;
import java.util.Objects;

/** Verde para {@code active} y rojo para el otro eje; dura {@code lights.greenSeconds} y sigue el amarillo. */
public record GreenState(Axis active) implements LightState {

    public GreenState {
        Objects.requireNonNull(active, "active");
    }

    @Override
    public LightColor colorFor(Axis axis) {
        return axis == active ? LightColor.GREEN : LightColor.RED;
    }

    @Override
    public Duration duration(LightTimings timings) {
        return timings.greenSeconds();
    }

    @Override
    public LightState next() {
        return new YellowState(active);
    }

    @Override
    public String name() {
        return "GREEN(" + active + ")";
    }
}
