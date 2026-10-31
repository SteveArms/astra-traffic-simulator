package com.astra.traffic;

import java.time.Duration;

/** Estado inmutable de un semáforo de dos ejes (patrón State). */
public interface LightState {

    LightColor colorFor(Axis axis);

    Duration duration(LightTimings timings);

    LightState next();

    default boolean canPass(Axis axis) {
        return colorFor(axis) == LightColor.GREEN;
    }

    String name();
}
