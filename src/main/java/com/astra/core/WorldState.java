package com.astra.core;

import com.astra.graph.RoadGraph;
import com.astra.model.Vehicle;
import com.astra.model.VehicleView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Grafo vial y vehículos en circulación.
 *
 * @implNote Su único dueño es astra-engine; los demás hilos usan {@link WorldSnapshot}.
 */
public final class WorldState {

    private final RoadGraph graph;
    private final List<Vehicle> vehicles = new ArrayList<>();
    private Consumer<Vehicle> onVehicleFinished = vehicle -> { };

    public WorldState(RoadGraph graph) {
        this.graph = Objects.requireNonNull(graph, "graph");
    }

    public RoadGraph graph() {
        return graph;
    }

    /** @implNote Called from astra-engine, or before the engine starts. */
    public void addVehicle(Vehicle vehicle) {
        vehicles.add(Objects.requireNonNull(vehicle, "vehicle"));
    }

    /**
     * Si el gancho no asigna una ruta nueva, el vehículo se elimina.
     *
     * @implNote The hook runs on astra-engine.
     */
    public void setOnVehicleFinished(Consumer<Vehicle> hook) {
        onVehicleFinished = Objects.requireNonNull(hook, "hook");
    }

    /** @implNote Called from astra-engine. */
    public void update(double dtSeconds) {
        // Tamaño fijo: el gancho puede agregar vehículos durante el recorrido.
        int count = vehicles.size();
        for (int i = 0; i < count; i++) {
            Vehicle vehicle = vehicles.get(i);
            vehicle.update(dtSeconds);
            if (vehicle.isFinished()) {
                onVehicleFinished.accept(vehicle);
            }
        }
        vehicles.removeIf(Vehicle::isFinished);
    }

    /** @implNote Called from astra-engine. */
    public WorldSnapshot toSnapshot(long tick, double simTimeSeconds) {
        List<VehicleView> views = new ArrayList<>(vehicles.size());
        for (Vehicle vehicle : vehicles) {
            views.add(vehicle.toView());
        }
        return new WorldSnapshot(tick, simTimeSeconds, views);
    }

    /** @implNote Called from astra-engine. */
    public int vehicleCount() {
        return vehicles.size();
    }

    /** @implNote Called from astra-engine, or from tests with the engine stopped. */
    public List<Vehicle> vehicles() {
        return Collections.unmodifiableList(vehicles);
    }
}
