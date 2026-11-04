package com.astra.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.astra.graph.Intersection;
import com.astra.graph.Road;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RoadOccupancyTest {

    private final Road road = new Road("R", new Intersection("A", 0, 0), new Intersection("B", 10, 0), 10, 14, 1);

    @Test
    void unknownRoadIsFree() {
        assertEquals(0, RoadOccupancy.EMPTY.of(road));
    }

    @Test
    void copiesTheMap() {
        var source = new HashMap<Road, Double>();
        source.put(road, 0.5);
        var occupancy = new RoadOccupancy(source);
        source.put(road, 1.0);

        assertEquals(0.5, occupancy.of(road));
        assertThrows(UnsupportedOperationException.class, () -> occupancy.ratios().clear());
    }

    @Test
    void ratioOutOfRangeThrows() {
        assertThrows(IllegalArgumentException.class, () -> new RoadOccupancy(Map.of(road, 1.5)));
        assertThrows(IllegalArgumentException.class, () -> new RoadOccupancy(Map.of(road, Double.NaN)));
    }
}
