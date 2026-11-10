package com.astra.traffic;

import com.astra.graph.Intersection;
import java.time.Duration;
import java.util.Objects;

/** Verde que se alarga mientras su eje tenga más demanda que el umbral y el contrario menos, hasta un máximo. */
public record SmartAdaptiveState(Axis active, Intersection at, QueueSensor sensor, SmartTimings timings,
        int extensionsUsed) implements LightState {

    public SmartAdaptiveState {
        Objects.requireNonNull(active, "active");
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(sensor, "sensor");
        Objects.requireNonNull(timings, "timings");
        if (extensionsUsed < 0) {
            throw new IllegalArgumentException("extensionsUsed must be >= 0: " + extensionsUsed);
        }
    }

    @Override
    public LightColor colorFor(Axis axis) {
        return axis == active ? LightColor.GREEN : LightColor.RED;
    }

    @Override
    public Duration duration(LightTimings lightTimings) {
        return extensionsUsed == 0 ? lightTimings.greenSeconds() : timings.smartExtensionStepSeconds();
    }

    /** @implNote Called from astra-lights-N. */
    @Override
    public LightState next() {
        int threshold = timings.smartQueueThreshold();
        boolean busy = sensor.queueLength(at, active) >= threshold;
        boolean oppositeCalm = sensor.queueLength(at, active.opposite()) < threshold;
        Duration total = timings.smartExtensionStepSeconds().multipliedBy(extensionsUsed + 1L);
        if (busy && oppositeCalm && total.compareTo(timings.smartMaxExtensionSeconds()) <= 0) {
            return new SmartAdaptiveState(active, at, sensor, timings, extensionsUsed + 1);
        }
        return new YellowState(active);
    }

    @Override
    public String name() {
        return "SMART-" + active + (extensionsUsed > 0 ? "+" + extensionsUsed : "");
    }
}
