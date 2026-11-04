package com.astra.model;

import com.astra.graph.Graph;
import com.astra.graph.Intersection;
import com.astra.graph.Road;
import com.astra.routing.RouteStrategy;
import java.util.List;
import java.util.Objects;

/** Crea vehículos con la especificación de su tipo y la ruta inicial calculada por su estrategia. */
public final class VehicleFactory {

    private final VehicleSpecs specs;

    /** @param specs normalmente el {@code SimConfig} */
    public VehicleFactory(VehicleSpecs specs) {
        this.specs = Objects.requireNonNull(specs, "specs");
    }

    /**
     * @throws IllegalStateException si la estrategia no encuentra ruta (incluye {@code origin == dest})
     * @implNote Called from astra-engine, or before the engine starts.
     */
    public Vehicle create(VehicleKind kind, RouteStrategy strategy, Intersection origin, Intersection dest,
            Graph<Intersection, Road> g) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(strategy, "strategy");
        List<Road> route = strategy.route(g, origin, dest);
        if (route.isEmpty()) {
            throw new IllegalStateException("No route from " + origin.id() + " to " + dest.id()
                    + " with " + strategy.name());
        }
        VehicleSpec spec = specs.specFor(kind);
        Vehicle vehicle = switch (kind) {
            case CAR -> new Car(spec, strategy, origin, dest);
            case BUS -> new Bus(spec, strategy, origin, dest);
            case AMBULANCE -> new Ambulance(spec, strategy, origin, dest);
        };
        vehicle.assignRoute(route);
        return vehicle;
    }
}
