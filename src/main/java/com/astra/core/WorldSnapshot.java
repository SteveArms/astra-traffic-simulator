package com.astra.core;

import com.astra.model.VehicleView;
import java.util.List;

/**
 * Foto inmutable del mundo en un tick. La produce {@code astra-engine} y la consume el
 * FX Application Thread; es seguro compartirla entre hilos porque es inmutable.
 */
public record WorldSnapshot(long tick, double simTimeSeconds, List<VehicleView> vehicles) {

    public static final WorldSnapshot EMPTY = new WorldSnapshot(0, 0, List.of());

    public WorldSnapshot {
        vehicles = List.copyOf(vehicles);
    }

    public int vehicleCount() {
        return vehicles.size();
    }
}
