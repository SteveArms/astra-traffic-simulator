package com.astra.routing;

import com.astra.graph.Graph;
import com.astra.graph.Intersection;
import com.astra.graph.Road;
import java.util.List;

/** Forma de calcular la ruta de un vehículo (patrón Strategy). */
public interface RouteStrategy {

    /** @return vías consecutivas de {@code from} a {@code to}; vacía si {@code from == to} o no hay camino */
    List<Road> route(Graph<Intersection, Road> g, Intersection from, Intersection to);

    /** Nombre estable para logs, HUD y {@link RouteStrategies#byName}. */
    String name();

    /** {@code true} si la ruta debe recalcularse en cada intersección. */
    default boolean isAdaptive() {
        return false;
    }
}
