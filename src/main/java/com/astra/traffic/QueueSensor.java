package com.astra.traffic;

import com.astra.graph.Intersection;

/** Vehículos que se acercan a una intersección por un eje. @implNote Called from astra-lights-N, sin bloquear. */
@FunctionalInterface
public interface QueueSensor {

    QueueSensor NONE = (at, axis) -> 0;

    int queueLength(Intersection at, Axis axis);
}
