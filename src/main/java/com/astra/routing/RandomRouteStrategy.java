package com.astra.routing;

import com.astra.graph.Graph;
import com.astra.graph.Intersection;
import com.astra.graph.Road;
import com.astra.graph.algo.Dijkstra;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/**
 * Rutas variadas pero razonables: Dijkstra con peso {@code length * (1 + U[0,1) * jitter)}, sorteado una vez por
 * vía en cada cálculo de ruta.
 */
public final class RandomRouteStrategy implements RouteStrategy {

    public static final String NAME = "RANDOM";

    private final Dijkstra<Intersection, Road> dijkstra = new Dijkstra<>();
    private final Random rnd;
    private final double jitter;

    public RandomRouteStrategy(Random rnd, double jitter) {
        this.rnd = Objects.requireNonNull(rnd, "rnd");
        if (!(jitter >= 0) || !Double.isFinite(jitter)) {
            throw new IllegalArgumentException("Random jitter must be >= 0: " + jitter);
        }
        this.jitter = jitter;
    }

    @Override
    public List<Road> route(Graph<Intersection, Road> g, Intersection from, Intersection to) {
        Map<Road, Double> weights = new HashMap<>();
        return dijkstra.shortestPath(g, from, to,
                road -> weights.computeIfAbsent(road, r -> r.length() * (1 + rnd.nextDouble() * jitter)));
    }

    @Override
    public String name() {
        return NAME;
    }
}
