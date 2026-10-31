package com.astra.traffic;

import java.time.Duration;

/**
 * Estado de un semáforo de dos ejes (patrón State). Cada estado decide el color de cada eje,
 * cuánto dura y cuál sigue. Las implementaciones son inmutables y seguras entre hilos.
 */
public interface LightState {

    LightColor colorFor(Axis axis);

    Duration duration(LightTimings timings);

    LightState next();

    default boolean canPass(Axis axis) {
        return colorFor(axis) == LightColor.GREEN;
    }

    /** Nombre para logs y HUD, por ejemplo {@code GREEN(NS)}. */
    String name();
}
