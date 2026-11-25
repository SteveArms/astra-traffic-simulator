package com.astra.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.astra.graph.Intersection;
import com.astra.graph.NodeKind;
import com.astra.graph.Road;
import com.astra.graph.RoadGraph;
import com.astra.graph.algo.Dijkstra;
import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class MapLoaderTest {

    private static final double DELTA = 1e-9;
    // Un gateway G, un cruce A y una playa P (el nodo P se completa con formatted).
    private static final String PARKING_MAP = """
            {"name": "Parking",
             "intersections": [{"id": "G", "x": 0, "y": 0, "kind": "GATEWAY"}, {"id": "A", "x": 10, "y": 0},
               %s],
             "roads": [{"id": "R1", "from": "G", "to": "A", "speedLimit": 10, "lanes": 1, "twoWay": true},
                       {"id": "R2", "from": "A", "to": "P", "speedLimit": 10, "lanes": 1, "twoWay": true}]}
            """;

    private static Set<Intersection> reachableFrom(RoadGraph graph, Intersection start) {
        Set<Intersection> seen = new HashSet<>(Set.of(start));
        Deque<Intersection> pending = new ArrayDeque<>(List.of(start));
        while (!pending.isEmpty()) {
            for (Road road : graph.outgoing(pending.poll())) {
                if (seen.add(road.to())) {
                    pending.add(road.to());
                }
            }
        }
        return seen;
    }

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
    void v2MapHasEveryNodeKindAndBounds() {
        MapLoader.LoadedMap map = MapLoader.load("maps/city-3x3-v2.json");
        Map<NodeKind, Long> byKind = map.graph().vertices().stream()
                .collect(Collectors.groupingBy(Intersection::kind, Collectors.counting()));

        assertEquals("City 3x3", map.name());
        assertEquals(new MapLoader.Bounds(1200, 900), map.bounds());
        assertEquals(Map.of(NodeKind.INTERSECTION, 9L, NodeKind.GATEWAY, 12L, NodeKind.JUNCTION, 4L,
                NodeKind.PARKING, 4L), byKind);
        // 12 calles (4 partidas en dos por un JUNCTION) + 12 gateways + 4 accesos a playas, en los dos sentidos.
        assertEquals(64, map.graph().edgeCount());
    }

    @Test
    void v2MapPlacesNodesOnTheGrid() {
        RoadGraph graph = MapLoader.load("maps/city-3x3-v2.json").graph();

        Intersection i12 = graph.findIntersection("I12").orElseThrow();
        assertEquals(900, i12.x(), DELTA);
        assertEquals(450, i12.y(), DELTA);
        assertEquals(0, graph.findIntersection("G-N1").orElseThrow().y(), DELTA);
        assertEquals(900, graph.findIntersection("G-S1").orElseThrow().y(), DELTA);
        assertEquals(0, graph.findIntersection("G-W2").orElseThrow().x(), DELTA);
        assertEquals(1200, graph.findIntersection("G-E2").orElseThrow().x(), DELTA);
        List<Integer> capacities = graph.vertices().stream().filter(Intersection::isParking)
                .map(Intersection::capacity).sorted().toList();
        assertEquals(List.of(6, 8, 10, 12), capacities);
        for (Intersection parking : graph.vertices().stream().filter(Intersection::isParking).toList()) {
            Road access = graph.outgoing(parking).get(0);
            assertEquals(NodeKind.JUNCTION, access.to().kind(), parking.id());
            assertEquals(70, access.length(), DELTA, parking.id());
        }
        assertTrue(graph.edges().stream().allMatch(r -> r.speedLimit() == 14 && r.lanes() == 1));
    }

    @Test
    void v2MapConnectsEveryGatewayToEveryGatewayAndParking() {
        RoadGraph graph = MapLoader.load("maps/city-3x3-v2.json").graph();
        Set<Intersection> targets = graph.vertices().stream()
                .filter(i -> i.isGateway() || i.isParking())
                .collect(Collectors.toSet());

        for (Intersection gateway : graph.vertices().stream().filter(Intersection::isGateway).toList()) {
            Set<Intersection> reached = reachableFrom(graph, gateway);
            assertTrue(reached.containsAll(targets), gateway.id() + " misses some gateway or parking");
        }
    }

    @Test
    void oldMapLoadsAsPlainIntersectionsWithDefaultBounds() {
        MapLoader.LoadedMap map = MapLoader.load("maps/city-3x3.json");

        assertTrue(map.graph().vertices().stream().allMatch(i -> i.kind() == NodeKind.INTERSECTION));
        assertTrue(map.graph().vertices().stream().allMatch(i -> i.capacity() == 0));
        // Mismo margen que el contenido a cada lado: x de 150 a 550, y de 100 a 500.
        assertEquals(new MapLoader.Bounds(700, 600), map.bounds());
    }

    @Test
    void kindAndCapacityAreParsed() {
        RoadGraph graph = parse(PARKING_MAP.formatted("""
                {"id": "P", "x": 10, "y": 50, "kind": "parking", "capacity": 4}""")).graph();

        Intersection parking = graph.findIntersection("P").orElseThrow();
        assertEquals(NodeKind.PARKING, parking.kind());
        assertEquals(4, parking.capacity());
        assertEquals(NodeKind.GATEWAY, graph.findIntersection("G").orElseThrow().kind());
    }

    @Test
    void parkingWithoutCapacityThrows() {
        var e = assertThrows(MapFormatException.class, () -> parse(PARKING_MAP.formatted("""
                {"id": "P", "x": 10, "y": 50, "kind": "PARKING"}""")));
        assertTrue(e.getMessage().contains("P"), e.getMessage());
    }

    @Test
    void parkingNeedsExactlyOneTwoWayRoad() {
        // Solo de ida: se llega pero no se sale.
        var oneWay = assertThrows(MapFormatException.class, () -> parse("""
                {"name": "Bad", "intersections": [{"id": "A", "x": 0, "y": 0},
                  {"id": "P", "x": 10, "y": 0, "kind": "PARKING", "capacity": 2}],
                 "roads": [{"id": "R1", "from": "A", "to": "P", "speedLimit": 10, "lanes": 1}]}
                """));
        assertTrue(oneWay.getMessage().contains("PARKING P must have exactly one two-way road"), oneWay.getMessage());
        // Dos vías de doble sentido: la playa sería un atajo.
        assertThrows(MapFormatException.class, () -> parse("""
                {"name": "Bad", "intersections": [{"id": "A", "x": 0, "y": 0}, {"id": "B", "x": 20, "y": 0},
                  {"id": "P", "x": 10, "y": 0, "kind": "PARKING", "capacity": 2}],
                 "roads": [{"id": "R1", "from": "A", "to": "P", "speedLimit": 10, "lanes": 1, "twoWay": true},
                           {"id": "R2", "from": "P", "to": "B", "speedLimit": 10, "lanes": 1, "twoWay": true}]}
                """));
    }

    @Test
    void gatewayNeedsExactlyOneTwoWayRoad() {
        var e = assertThrows(MapFormatException.class, () -> parse("""
                {"name": "Bad", "intersections": [{"id": "A", "x": 0, "y": 0}, {"id": "B", "x": 20, "y": 0},
                  {"id": "G", "x": 10, "y": 0, "kind": "GATEWAY"}],
                 "roads": [{"id": "R1", "from": "A", "to": "G", "speedLimit": 10, "lanes": 1, "twoWay": true},
                           {"id": "R2", "from": "G", "to": "B", "speedLimit": 10, "lanes": 1, "twoWay": true}]}
                """));
        assertTrue(e.getMessage().contains("GATEWAY G must have exactly one two-way road"), e.getMessage());
    }

    @Test
    void unknownKindThrows() {
        assertThrows(MapFormatException.class, () -> parse("""
                {"name": "Bad", "intersections": [{"id": "A", "x": 0, "y": 0},
                  {"id": "B", "x": 10, "y": 0, "kind": "ROUNDABOUT"}],
                 "roads": [{"id": "R1", "from": "A", "to": "B", "speedLimit": 10, "lanes": 1}]}
                """));
    }

    @Test
    void explicitBoundsAreUsedAndNodesMustFit() {
        MapLoader.LoadedMap map = parse("""
                {"name": "Box", "bounds": {"width": 100, "height": 80},
                 "intersections": [{"id": "A", "x": 0, "y": 0}, {"id": "B", "x": 100, "y": 80}],
                 "roads": [{"id": "R1", "from": "A", "to": "B", "speedLimit": 10, "lanes": 1}]}
                """);
        assertEquals(new MapLoader.Bounds(100, 80), map.bounds());

        assertThrows(MapFormatException.class, () -> parse("""
                {"name": "Out", "bounds": {"width": 50, "height": 80},
                 "intersections": [{"id": "A", "x": 0, "y": 0}, {"id": "B", "x": 100, "y": 80}],
                 "roads": [{"id": "R1", "from": "A", "to": "B", "speedLimit": 10, "lanes": 1}]}
                """));
        assertThrows(MapFormatException.class, () -> parse("""
                {"name": "Zero", "bounds": {"width": 0, "height": 80},
                 "intersections": [{"id": "A", "x": 0, "y": 0}, {"id": "B", "x": 0, "y": 80}],
                 "roads": [{"id": "R1", "from": "A", "to": "B", "speedLimit": 10, "lanes": 1}]}
                """));
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
