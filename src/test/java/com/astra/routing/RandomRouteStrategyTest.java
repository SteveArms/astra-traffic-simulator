package com.astra.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.astra.graph.Intersection;
import com.astra.graph.Road;
import com.astra.graph.RoadGraph;
import com.astra.util.MapLoader;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RandomRouteStrategyTest {

    private static final double JITTER = 0.6;

    private final RoadGraph city = MapLoader.load("maps/city-3x3.json").graph();

    private Intersection at(String id) {
        return city.findIntersection(id).orElseThrow();
    }

    private List<Road> route(long seed, Intersection from, Intersection to) {
        return new RandomRouteStrategy(new Random(seed), JITTER).route(city, from, to);
    }

    @Test
    void sameSeedGivesSameRoute() {
        assertEquals(route(7, at("I00"), at("I22")), route(7, at("I00"), at("I22")));
    }

    @Test
    void differentSeedsGiveMoreThanOneRoute() {
        Set<List<Road>> routes = new HashSet<>();
        for (long seed = 0; seed < 20; seed++) {
            routes.add(route(seed, at("I00"), at("I22")));
        }

        assertTrue(routes.size() > 1, "only one route: " + routes);
    }

    @Test
    void everyRouteIsConnectedAndReachesDestination() {
        var strategy = new RandomRouteStrategy(new Random(42), JITTER);

        for (Intersection from : city.vertices()) {
            for (Intersection to : city.vertices()) {
                if (from.equals(to)) {
                    continue;
                }
                List<Road> route = strategy.route(city, from, to);
                assertFalse(route.isEmpty(), from.id() + " -> " + to.id());
                assertEquals(from, route.get(0).from());
                for (int i = 1; i < route.size(); i++) {
                    assertEquals(route.get(i - 1).to(), route.get(i).from(), route.toString());
                }
                assertEquals(to, route.get(route.size() - 1).to());
            }
        }
    }

    @Test
    void negativeJitterThrows() {
        assertThrows(IllegalArgumentException.class, () -> new RandomRouteStrategy(new Random(), -0.1));
    }
}
