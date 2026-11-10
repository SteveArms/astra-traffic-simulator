package com.astra.core;

import com.astra.model.VehicleKind;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Foto inmutable de {@link SimStats}; los promedios son de los vehículos que ya llegaron. */
public record StatsView(double avgTripSeconds, double avgWaitSeconds, Map<VehicleKind, Double> avgWaitByKind,
        double throughputPerMin, long totalArrived, int currentQueue, int maxQueue, List<StatsPoint> series) {

    public static final StatsView EMPTY = new StatsView(0, 0, Map.of(), 0, 0, 0, 0, List.of());

    public StatsView {
        avgWaitByKind = Map.copyOf(Objects.requireNonNull(avgWaitByKind, "avgWaitByKind"));
        series = List.copyOf(Objects.requireNonNull(series, "series"));
    }
}
