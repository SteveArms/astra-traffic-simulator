package com.astra.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class RouteStrategiesTest {

    private record Settings(double congestionFactor, double randomJitter) implements RoutingSettings {
    }

    private final RoutingSettings settings = new Settings(2.0, 0.6);

    private RouteStrategy byName(String name) {
        return RouteStrategies.byName(name, () -> RoadOccupancy.EMPTY, settings, new Random(1));
    }

    @Test
    void createsEachStrategyByName() {
        assertInstanceOf(ShortestDistanceStrategy.class, byName("SHORTEST"));
        assertInstanceOf(LeastCongestedStrategy.class, byName("LEAST_CONGESTED"));
        assertInstanceOf(RandomRouteStrategy.class, byName("RANDOM"));
    }

    @Test
    void nameRoundTrips() {
        for (String name : List.of("SHORTEST", "LEAST_CONGESTED", "RANDOM")) {
            assertEquals(name, byName(name).name());
        }
        assertEquals("RANDOM", byName(" random ").name());
    }

    @Test
    void unknownNameThrows() {
        assertThrows(IllegalArgumentException.class, () -> byName("FASTEST"));
    }
}
