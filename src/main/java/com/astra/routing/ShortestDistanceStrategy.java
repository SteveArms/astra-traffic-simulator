package com.astra.routing;

import com.astra.graph.Graph;
import com.astra.graph.Intersection;
import com.astra.graph.Road;
import com.astra.graph.algo.Dijkstra;
import java.util.List;

/** Ruta de menor longitud total (Dijkstra). Sin estado: thread-safe. */
public final class ShortestDistanceStrategy implements RouteStrategy {

    public static final String NAME = "SHORTEST";

    private final Dijkstra<Intersection, Road> dijkstra = new Dijkstra<>();

    @Override
    public List<Road> route(Graph<Intersection, Road> g, Intersection from, Intersection to) {
        return dijkstra.shortestPath(g, from, to, Road::length);
    }

    @Override
    public String name() {
        return NAME;
    }
}
