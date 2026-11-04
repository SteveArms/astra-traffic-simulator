package com.astra.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.astra.graph.Intersection;
import com.astra.graph.Road;
import com.astra.graph.RoadGraph;
import com.astra.util.MapLoader;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class LeastCongestedStrategyTest {

    private static final double FACTOR = 2.0;

    private final RoadGraph city = MapLoader.load("maps/city-3x3.json").graph();

    private Intersection at(String id) {
        return city.findIntersection(id).orElseThrow();
    }

    private static double length(List<Road> route) {
        return route.stream().mapToDouble(Road::length).sum();
    }

    @Test
    void avoidsCongestedCentralRoads() {
        Intersection center = at("I11");
        Map<Road, Double> full = city.edges().stream()
                .filter(r -> r.from().equals(center) || r.to().equals(center))
                .collect(Collectors.toMap(Function.identity(), r -> 1.0));
        var strategy = new LeastCongestedStrategy(() -> new RoadOccupancy(full), FACTOR);

        List<Road> route = strategy.route(city, at("I01"), at("I21"));

        assertTrue(route.stream().noneMatch(full::containsKey), route.toString());
        assertEquals(800, length(route), 1e-9);
    }

    @Test
    void withoutOccupancyMatchesShortestCostForAllPairs() {
        var congested = new LeastCongestedStrategy(() -> RoadOccupancy.EMPTY, FACTOR);
        var shortest = new ShortestDistanceStrategy();

        for (Intersection from : city.vertices()) {
            for (Intersection to : city.vertices()) {
                assertEquals(length(shortest.route(city, from, to)), length(congested.route(city, from, to)), 1e-9);
            }
        }
    }

    @Test
    void isAdaptive() {
        var strategy = new LeastCongestedStrategy(() -> RoadOccupancy.EMPTY, FACTOR);

        assertTrue(strategy.isAdaptive());
        assertEquals("LEAST_CONGESTED", strategy.name());
    }

    @Test
    void negativeFactorThrows() {
        assertThrows(IllegalArgumentException.class, () -> new LeastCongestedStrategy(() -> RoadOccupancy.EMPTY, -1));
    }
}
