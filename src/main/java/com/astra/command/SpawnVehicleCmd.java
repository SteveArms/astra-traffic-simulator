package com.astra.command;

import com.astra.core.SimulationEngine;
import com.astra.core.VehicleSpawner;
import com.astra.graph.Intersection;
import com.astra.graph.RoadGraph;
import com.astra.model.VehicleKind;
import com.astra.routing.RouteStrategies;
import java.util.Objects;
import java.util.Optional;

/**
 * Genera un vehículo a pedido. {@code originId} y {@code destId} pueden ser {@code null}: se sortean entre los
 * bordes del mapa. Se ignora (y queda en el historial) si un id no existe o no hay spawner, ruta o espacio.
 *
 * @param strategyName se guarda normalizado ({@code "random"} → {@code "RANDOM"})
 */
public record SpawnVehicleCmd(VehicleKind kind, String strategyName, String originId, String destId)
        implements Command {

    public SpawnVehicleCmd {
        Objects.requireNonNull(kind, "kind");
        strategyName = RouteStrategies.canonicalName(strategyName);
        if (originId != null && originId.equals(destId)) {
            throw new IllegalArgumentException("origin and destination are the same: " + originId);
        }
    }

    @Override
    public void execute(SimulationEngine engine) {
        CommandBus bus = engine.commandBus();
        Optional<VehicleSpawner> spawner = engine.spawner();
        if (spawner.isEmpty()) {
            bus.markIgnored("no spawner");
            return;
        }
        RoadGraph graph = engine.graph();
        Optional<Intersection> origin = graph.findIntersection(originId);
        Optional<Intersection> dest = graph.findIntersection(destId);
        if (originId != null && origin.isEmpty()) {
            bus.markIgnored("unknown intersection " + originId);
            return;
        }
        if (destId != null && dest.isEmpty()) {
            bus.markIgnored("unknown intersection " + destId);
            return;
        }
        VehicleSpawner s = spawner.get();
        if (s.spawnNow(kind, s.strategyNamed(strategyName), origin.orElse(null), dest.orElse(null)).isEmpty()) {
            bus.markIgnored("no room, no route or vehicle limit reached");
        }
    }

    @Override
    public String describe() {
        return "Spawn(" + kind + ", " + strategyName + ", " + label(originId) + " -> " + label(destId) + ")";
    }

    private static String label(String id) {
        return id == null ? "random" : id;
    }
}
