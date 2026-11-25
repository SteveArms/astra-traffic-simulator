package com.astra.core;

import com.astra.model.VehicleKind;
import com.astra.model.VehicleView;
import com.astra.traffic.LightMode;
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
 * @param mutexes      exclusión mutua de Ricart-Agrawala por intersección, ordenada por id
 * @param parkings     playas con sus autos estacionados, ordenadas por id
 */
public record WorldSnapshot(long tick, double simTimeSeconds, List<VehicleView> vehicles, List<LightView> lights,
        long spawned, long despawned, Map<VehicleKind, Integer> activeByKind, boolean paused, double speedFactor,
        StatsView stats, LightMode lightMode, MutexMode mutexMode, List<MutexView> mutexes, NetworkView network,
        List<ParkingView> parkings) {

    public static final WorldSnapshot EMPTY =
            new WorldSnapshot(0, 0, List.of(), List.of(), 0, 0, Map.of(), false, 1.0, StatsView.EMPTY,
                    LightMode.FIXED);

    public WorldSnapshot {
        vehicles = List.copyOf(vehicles);
        lights = List.copyOf(lights);
        activeByKind = Map.copyOf(activeByKind);
        Objects.requireNonNull(stats, "stats");
        Objects.requireNonNull(lightMode, "lightMode");
        Objects.requireNonNull(mutexMode, "mutexMode");
        mutexes = List.copyOf(mutexes);
        Objects.requireNonNull(network, "network");
        parkings = List.copyOf(parkings);
    }

    /** Sin capa distribuida: modo SEMAPHORE, sin mutex ni tráfico de red. */
    public WorldSnapshot(long tick, double simTimeSeconds, List<VehicleView> vehicles, List<LightView> lights,
            long spawned, long despawned, Map<VehicleKind, Integer> activeByKind, boolean paused, double speedFactor,
            StatsView stats, LightMode lightMode) {
        this(tick, simTimeSeconds, vehicles, lights, spawned, despawned, activeByKind, paused, speedFactor, stats,
                lightMode, MutexMode.SEMAPHORE, List.of(), NetworkView.EMPTY, List.of());
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
