package com.astra.core;

import com.astra.graph.Intersection;
import com.astra.graph.RoadGraph;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Una {@link IntersectionZone} por intersección del grafo. */
public final class IntersectionZones {

    private final Map<Intersection, IntersectionZone> zones = new ConcurrentHashMap<>();

    public IntersectionZones(RoadGraph graph, SimConfig cfg) {
        for (Intersection intersection : graph.vertices()) {
            zones.put(intersection, new IntersectionZone(intersection, cfg.intersectionCapacity()));
        }
    }

    public IntersectionZone zoneOf(Intersection intersection) {
        IntersectionZone zone = zones.get(intersection);
        if (zone == null) {
            throw new IllegalArgumentException("No zone for intersection: " + intersection);
        }
        return zone;
    }

    /** @implNote Called from astra-engine when a vehicle is removed. */
    public void releaseAll(String vehicleId) {
        zones.values().forEach(zone -> zone.leave(vehicleId));
    }

    public Collection<IntersectionZone> zones() {
        return zones.values();
    }
}
