package com.astra.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.astra.graph.Intersection;
import com.astra.graph.Road;
import com.astra.graph.RoadGraph;
import com.astra.util.MapLoader;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShortestDistanceStrategyTest {

    private final RoadGraph city = MapLoader.load("maps/city-3x3.json").graph();
    private final RouteStrategy strategy = new ShortestDistanceStrategy();

    private Intersection at(String id) {
        return city.findIntersection(id).orElseThrow();
    }

    @Test
    void cornerToCornerIs800() {
        List<Road> route = strategy.route(city, at("I00"), at("I22"));

        assertEquals(4, route.size());
        assertEquals(800, route.stream().mapToDouble(Road::length).sum(), 1e-9);
    }

    @Test
    void sameIntersectionGivesEmptyRoute() {
        assertTrue(strategy.route(city, at("I11"), at("I11")).isEmpty());
    }

    @Test
    void nameAndAdaptivity() {
        assertEquals("SHORTEST", strategy.name());
        assertFalse(strategy.isAdaptive());
    }
}
