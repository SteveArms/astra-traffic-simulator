package com.astra.core;

import com.astra.traffic.LightColor;
import java.util.Objects;

/** Semáforo y ocupación de su zona en un instante; parte de {@link WorldSnapshot}. */
public record LightView(String intersectionId, double x, double y, LightColor ns, LightColor ew,
        String stateName, int zoneOccupancy, int zoneCapacity) {

    public LightView {
        Objects.requireNonNull(intersectionId, "intersectionId");
        Objects.requireNonNull(ns, "ns");
        Objects.requireNonNull(ew, "ew");
        Objects.requireNonNull(stateName, "stateName");
    }

    public boolean zoneOccupied() {
        return zoneOccupancy > 0;
    }

    public boolean zoneFull() {
        return zoneOccupancy >= zoneCapacity;
    }
}
