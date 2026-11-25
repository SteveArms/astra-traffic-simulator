package com.astra.core;

import com.astra.model.VehicleKind;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Foto inmutable de {@link SimStats}; los promedios son de los vehículos que ya llegaron.
 *
 * @param avgParkingSearchSeconds tiempo medio desde el primer intento de estacionar hasta que lo logra
 * @param parkingRejections       llegadas a una playa llena o cerrada
 * @param avgParkingOccupancy     ocupación media de todas las playas juntas (0 a 1), ponderada por tiempo
 */
public record StatsView(double avgTripSeconds, double avgWaitSeconds, Map<VehicleKind, Double> avgWaitByKind,
        double throughputPerMin, long totalArrived, int currentQueue, int maxQueue, List<StatsPoint> series,
        double avgParkingSearchSeconds, long parkingRejections, double avgParkingOccupancy) {

    public static final StatsView EMPTY = new StatsView(0, 0, Map.of(), 0, 0, 0, 0, List.of(), 0, 0, 0);

    public StatsView {
        avgWaitByKind = Map.copyOf(Objects.requireNonNull(avgWaitByKind, "avgWaitByKind"));
        series = List.copyOf(Objects.requireNonNull(series, "series"));
    }
}
