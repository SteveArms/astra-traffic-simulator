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
 * Estado mutable del mundo: el grafo vial y los vehículos en circulación.
 *
 * @implNote Su único dueño es el hilo {@code astra-engine}: la lista de vehículos no está
 * sincronizada y solo se toca desde el motor (o desde el hilo de configuración antes de
 * arrancarlo). Los demás hilos ven el mundo a través de {@link WorldSnapshot}.
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

    /** @implNote Called from astra-engine, or from the setup thread before the engine starts. */
    public void addVehicle(Vehicle vehicle) {
        vehicles.add(Objects.requireNonNull(vehicle, "vehicle"));
    }

    /**
     * Se invoca cuando un vehículo termina su ruta. Si el gancho le asigna una ruta nueva sigue
     * en el mundo; si no, se elimina.
     *
     * @implNote Called from the setup thread before the engine starts; the hook runs on astra-engine.
     */
    public void setOnVehicleFinished(Consumer<Vehicle> hook) {
        onVehicleFinished = Objects.requireNonNull(hook, "hook");
    }

    /** @implNote Called from astra-engine. */
    public void update(double dtSeconds) {
        // Índice fijo: el gancho puede agregar vehículos, que empiezan a moverse en el siguiente tick.
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

    /**
     * Vista de solo lectura.
     *
     * @implNote Called from astra-engine, or from tests with the engine stopped.
     */
    public List<Vehicle> vehicles() {
        return Collections.unmodifiableList(vehicles);
    }
}
