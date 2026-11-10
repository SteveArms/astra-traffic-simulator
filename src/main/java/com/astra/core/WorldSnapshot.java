package com.astra.core;

import com.astra.model.VehicleKind;
import com.astra.model.VehicleView;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Foto inmutable del mundo: la produce astra-engine y la lee el FX Thread sin sincronizar.
 *
 * @param spawned      vehículos agregados desde el inicio
 * @param despawned    vehículos que terminaron su ruta y salieron
 * @param activeByKind vehículos en circulación por tipo
 * @param paused       el mundo está congelado (el tick sigue avanzando, el tiempo simulado no)
 * @param speedFactor  multiplicador de velocidad vigente
 */
public record WorldSnapshot(long tick, double simTimeSeconds, List<VehicleView> vehicles, List<LightView> lights,
        long spawned, long despawned, Map<VehicleKind, Integer> activeByKind, boolean paused, double speedFactor,
        StatsView stats) {

    public static final WorldSnapshot EMPTY =
            new WorldSnapshot(0, 0, List.of(), List.of(), 0, 0, Map.of(), false, 1.0, StatsView.EMPTY);

    public WorldSnapshot {
        vehicles = List.copyOf(vehicles);
        lights = List.copyOf(lights);
        activeByKind = Map.copyOf(activeByKind);
        Objects.requireNonNull(stats, "stats");
    }

    public int vehicleCount() {
        return vehicles.size();
    }

    public int lightCount() {
        return lights.size();
    }

    /** @return 0 si no hay vehículos de ese tipo */
    public int activeCount(VehicleKind kind) {
        return activeByKind.getOrDefault(kind, 0);
    }
}
