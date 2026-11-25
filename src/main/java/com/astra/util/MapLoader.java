package com.astra.util;

import com.astra.graph.Intersection;
import com.astra.graph.NodeKind;
import com.astra.graph.Road;
import com.astra.graph.RoadGraph;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Carga mapas JSON en un {@link RoadGraph}. Cada nodo acepta {@code kind} ({@link NodeKind}, por defecto
 * INTERSECTION) y {@code capacity} (solo PARKING); el mapa acepta {@code bounds} con su ancho y alto. Una PARKING o
 * una GATEWAY debe tener exactamente una vía de doble sentido.
 */
public final class MapLoader {

    private static final String BASE_PATH = "/com/astra/";
    private static final Gson GSON = new Gson();
    // Sin bounds en el JSON: el mismo margen a ambos lados del contenido, o este si el contenido toca el origen.
    private static final double DEFAULT_BOUNDS_MARGIN = 50;

    /** Tamaño del lienzo del mapa; todos los nodos están dentro de {@code [0, width] x [0, height]}. */
    public record Bounds(double width, double height) {

        public Bounds {
            if (!(width > 0) || !(height > 0) || !Double.isFinite(width) || !Double.isFinite(height)) {
                throw new IllegalArgumentException("Bounds must be positive: " + width + " x " + height);
            }
        }
    }

    public record LoadedMap(String name, RoadGraph graph, Bounds bounds) {
    }

    private record MapDto(String name, BoundsDto bounds, List<IntersectionDto> intersections, List<RoadDto> roads) {
    }

    private record BoundsDto(Double width, Double height) {
    }

    private record IntersectionDto(String id, Double x, Double y, String kind, Integer capacity) {
    }

    private record RoadDto(String id, String from, String to, Double speedLimit, Integer lanes, Boolean twoWay,
            Double length) {
    }

    private MapLoader() {
    }

    /** Las rutas relativas parten de {@code /com/astra/}. */
    public static LoadedMap load(String classpathResource) {
        String path = classpathResource.startsWith("/") ? classpathResource : BASE_PATH + classpathResource;
        try (InputStream in = MapLoader.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new MapFormatException("Map resource not found: " + path);
            }
            return parse(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new MapFormatException("Unable to read map " + path, e);
        }
    }

    public static LoadedMap parse(Reader reader) {
        MapDto dto;
        try {
            dto = GSON.fromJson(reader, MapDto.class);
        } catch (JsonParseException e) {
            throw new MapFormatException("Invalid map JSON: " + e.getMessage(), e);
        }
        if (dto == null) {
            throw new MapFormatException("Map JSON is empty");
        }
        String name = require(dto.name(), "map name");
        RoadGraph graph = buildGraph(dto);
        validateDeadEnds(graph);
        return new LoadedMap(name, graph, boundsOf(dto.bounds(), graph));
    }

    private static RoadGraph buildGraph(MapDto dto) {
        if (dto.intersections() == null || dto.intersections().isEmpty()) {
            throw new MapFormatException("Map has no intersections");
        }
        if (dto.roads() == null || dto.roads().isEmpty()) {
            throw new MapFormatException("Map has no roads");
        }

        var graph = new RoadGraph();
        Map<String, Intersection> byId = new HashMap<>();
        for (IntersectionDto dtoI : dto.intersections()) {
            String id = require(dtoI.id(), "intersection id");
            if (byId.containsKey(id)) {
                throw new MapFormatException("Duplicate intersection id: " + id);
            }
            double x = require(dtoI.x(), "x of intersection " + id);
            double y = require(dtoI.y(), "y of intersection " + id);
            NodeKind kind = kindOf(dtoI.kind(), id);
            int capacity = dtoI.capacity() == null ? 0 : dtoI.capacity();
            try {
                var intersection = new Intersection(id, x, y, kind, capacity);
                byId.put(id, intersection);
                graph.addVertex(intersection);
            } catch (IllegalArgumentException e) {
                throw new MapFormatException("Invalid intersection " + id + ": " + e.getMessage(), e);
            }
        }

        Set<String> roadIds = new HashSet<>();
        for (RoadDto dtoR : dto.roads()) {
            String id = require(dtoR.id(), "road id");
            if (!roadIds.add(id)) {
                throw new MapFormatException("Duplicate road id: " + id);
            }
            Intersection from = resolve(byId, dtoR.from(), id);
            Intersection to = resolve(byId, dtoR.to(), id);
            double length = dtoR.length() != null ? dtoR.length() : from.distanceTo(to);
            double speedLimit = require(dtoR.speedLimit(), "speedLimit of road " + id);
            int lanes = require(dtoR.lanes(), "lanes of road " + id);
            try {
                if (Boolean.TRUE.equals(dtoR.twoWay())) {
                    graph.addTwoWayRoad(id, from, to, length, speedLimit, lanes);
                } else {
                    graph.addEdge(new Road(id, from, to, length, speedLimit, lanes));
                }
            } catch (IllegalArgumentException e) {
                throw new MapFormatException("Invalid road " + id + ": " + e.getMessage(), e);
            }
        }
        return graph;
    }

    private static NodeKind kindOf(String raw, String id) {
        if (raw == null) {
            return NodeKind.INTERSECTION;
        }
        try {
            return NodeKind.valueOf(raw.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new MapFormatException("Invalid kind of intersection " + id + ": " + raw, e);
        }
    }

    // Una playa o un gateway es un extremo: solo una vía de doble sentido lo une al resto.
    private static void validateDeadEnds(RoadGraph graph) {
        Map<Intersection, List<Road>> incoming = new HashMap<>();
        for (Road road : graph.edges()) {
            incoming.computeIfAbsent(road.to(), at -> new ArrayList<>()).add(road);
        }
        for (Intersection node : graph.vertices()) {
            if (!node.isParking() && !node.isGateway()) {
                continue;
            }
            List<Road> out = graph.outgoing(node);
            List<Road> in = incoming.getOrDefault(node, List.of());
            boolean oneTwoWay = out.size() == 1 && in.size() == 1 && out.get(0).to().equals(in.get(0).from());
            if (!oneTwoWay) {
                throw new MapFormatException(node.kind() + " " + node.id()
                        + " must have exactly one two-way road, found " + out.size() + " out and " + in.size()
                        + " in");
            }
        }
    }

    private static Bounds boundsOf(BoundsDto dto, RoadGraph graph) {
        double maxX = graph.vertices().stream().mapToDouble(Intersection::x).max().orElse(0);
        double maxY = graph.vertices().stream().mapToDouble(Intersection::y).max().orElse(0);
        double minX = graph.vertices().stream().mapToDouble(Intersection::x).min().orElse(0);
        double minY = graph.vertices().stream().mapToDouble(Intersection::y).min().orElse(0);
        Bounds bounds;
        try {
            bounds = dto == null
                    ? new Bounds(maxX + margin(minX), maxY + margin(minY))
                    : new Bounds(require(dto.width(), "bounds width"), require(dto.height(), "bounds height"));
        } catch (IllegalArgumentException e) {
            throw new MapFormatException("Invalid bounds: " + e.getMessage(), e);
        }
        for (Intersection node : graph.vertices()) {
            if (node.x() < 0 || node.y() < 0 || node.x() > bounds.width() || node.y() > bounds.height()) {
                throw new MapFormatException("Intersection " + node.id() + " is outside the bounds "
                        + bounds.width() + " x " + bounds.height());
            }
        }
        return bounds;
    }

    private static double margin(double min) {
        return min > 0 ? min : DEFAULT_BOUNDS_MARGIN;
    }

    private static Intersection resolve(Map<String, Intersection> byId, String ref, String roadId) {
        Intersection intersection = byId.get(require(ref, "endpoint of road " + roadId));
        if (intersection == null) {
            throw new MapFormatException("Road " + roadId + " references unknown intersection: " + ref);
        }
        return intersection;
    }

    private static <T> T require(T value, String what) {
        if (value == null || (value instanceof String s && s.isBlank())) {
            throw new MapFormatException("Missing " + what);
        }
        return value;
    }
}
