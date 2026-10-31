package com.astra.core;

import com.astra.model.VehicleView;
import java.util.List;

/** Foto inmutable del mundo: la produce astra-engine y la lee el FX Thread sin sincronizar. */
public record WorldSnapshot(long tick, double simTimeSeconds, List<VehicleView> vehicles, List<LightView> lights) {

    public static final WorldSnapshot EMPTY = new WorldSnapshot(0, 0, List.of(), List.of());

    public WorldSnapshot {
        vehicles = List.copyOf(vehicles);
        lights = List.copyOf(lights);
    }

    public int vehicleCount() {
        return vehicles.size();
    }

    public int lightCount() {
        return lights.size();
    }
}
