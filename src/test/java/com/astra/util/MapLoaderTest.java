package com.astra.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.astra.graph.Intersection;
import com.astra.graph.Road;
import com.astra.graph.RoadGraph;
import com.astra.graph.algo.Dijkstra;
import java.io.StringReader;
import java.util.List;
import org.junit.jupiter.api.Test;

class MapLoaderTest {

    private static final double DELTA = 1e-9;

    private static MapLoader.LoadedMap parse(String json) {
        return MapLoader.parse(new StringReader(json));
    }

    @Test
    void realMapLoads() {
        MapLoader.LoadedMap map = MapLoader.load("maps/city-3x3.json");

        assertEquals("City 3x3", map.name());
        assertEquals(9, map.graph().vertexCount());
        assertEquals(24, map.graph().edgeCount());
    }

    @Test
    void shortestPathAcrossRealMapIs800() {
        RoadGraph graph = MapLoader.load("maps/city-3x3.json").graph();
        Intersection start = graph.findIntersection("I00").orElseThrow();
        Intersection end = graph.findIntersection("I22").orElseThrow();

        List<Road> path = new Dijkstra<Intersection, Road>().shortestPath(graph, start, end);

        assertEquals(4, path.size());
        assertEquals(800, Dijkstra.totalWeight(path, Road::weight), DELTA);
    }

    @Test
    void explicitLengthOverridesDistanceAndOneWayIsDefault() {
        RoadGraph graph = parse("""
                {"name": "Mini",
                 "intersections": [{"id": "A", "x": 0, "y": 0}, {"id": "B", "x": 30, "y": 40}],
                 "roads": [{"id": "R1", "from": "A", "to": "B", "speedLimit": 10, "lanes": 1, "length": 75}]}
                """).graph();

        assertEquals(1, graph.edgeCount());
        assertEquals(75, graph.edges().get(0).length(), DELTA);
    }

    @Test
    void duplicateIntersectionIdThrows() {
        var e = assertThrows(MapFormatException.class, () -> parse("""
                {"name": "Dup",
                 "intersections": [{"id": "A", "x": 0, "y": 0}, {"id": "A", "x": 10, "y": 0}],
                 "roads": [{"id": "R1", "from": "A", "to": "A", "speedLimit": 10, "lanes": 1}]}
                """));
        assertTrue(e.getMessage().contains("Duplicate intersection id: A"), e.getMessage());
    }

    @Test
    void duplicateRoadIdThrows() {
        var e = assertThrows(MapFormatException.class, () -> parse("""
                {"name": "Dup",
                 "intersections": [{"id": "A", "x": 0, "y": 0}, {"id": "B", "x": 10, "y": 0}],
                 "roads": [{"id": "R1", "from": "A", "to": "B", "speedLimit": 10, "lanes": 1},
                           {"id": "R1", "from": "B", "to": "A", "speedLimit": 10, "lanes": 1}]}
                """));
        assertTrue(e.getMessage().contains("Duplicate road id: R1"), e.getMessage());
    }

    @Test
    void brokenReferenceThrows() {
        var e = assertThrows(MapFormatException.class, () -> parse("""
                {"name": "Broken",
                 "intersections": [{"id": "A", "x": 0, "y": 0}],
                 "roads": [{"id": "R1", "from": "A", "to": "Z", "speedLimit": 10, "lanes": 1}]}
                """));
        assertTrue(e.getMessage().contains("unknown intersection: Z"), e.getMessage());
    }

    @Test
    void emptyListsThrow() {
        assertThrows(MapFormatException.class, () -> parse("""
                {"name": "Empty", "intersections": [], "roads": []}
                """));
        assertThrows(MapFormatException.class, () -> parse("""
                {"name": "NoRoads", "intersections": [{"id": "A", "x": 0, "y": 0}], "roads": []}
                """));
    }

    @Test
    void invalidRoadDataAndMalformedJsonThrow() {
        assertThrows(MapFormatException.class, () -> parse("""
                {"name": "Bad",
                 "intersections": [{"id": "A", "x": 0, "y": 0}, {"id": "B", "x": 10, "y": 0}],
                 "roads": [{"id": "R1", "from": "A", "to": "B", "speedLimit": 0, "lanes": 1}]}
                """));
        assertThrows(MapFormatException.class, () -> parse("{ not json"));
        assertThrows(MapFormatException.class, () -> MapLoader.load("maps/missing.json"));
    }
}
