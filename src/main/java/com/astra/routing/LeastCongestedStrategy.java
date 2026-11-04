package com.astra.routing;

import com.astra.graph.Graph;
import com.astra.graph.Heuristics;
import com.astra.graph.Intersection;
import com.astra.graph.Road;
import com.astra.graph.algo.AStar;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Evita vías ocupadas: A* con peso {@code length * (1 + factor * ocupación)}. El heurístico euclidiano sigue
 * siendo admisible porque ese peso nunca es menor que {@code length}.
 */
public final class LeastCongestedStrategy implements RouteStrategy {

    public static final String NAME = "LEAST_CONGESTED";

    private final AStar<Intersection, Road> aStar = new AStar<>();
    private final Supplier<RoadOccupancy> occupancy;
    private final double factor;

    /** @param occupancy se consulta una vez por cálculo de ruta; debe ser seguro llamarlo desde el hilo que enruta */
    public LeastCongestedStrategy(Supplier<RoadOccupancy> occupancy, double factor) {
        this.occupancy = Objects.requireNonNull(occupancy, "occupancy");
        if (!(factor >= 0) || !Double.isFinite(factor)) {
            throw new IllegalArgumentException("Congestion factor must be >= 0: " + factor);
        }
        this.factor = factor;
    }

    @Override
    public List<Road> route(Graph<Intersection, Road> g, Intersection from, Intersection to) {
        RoadOccupancy current = Objects.requireNonNull(occupancy.get(), "occupancy snapshot");
        return aStar.shortestPath(g, from, to, road -> road.length() * (1 + factor * current.of(road)),
                Heuristics.euclidean());
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isAdaptive() {
        return true;
    }
}
