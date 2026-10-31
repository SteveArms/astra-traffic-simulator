package com.astra.util;

import com.astra.graph.Intersection;
import com.astra.graph.Road;
import com.astra.graph.RoadGraph;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Carga mapas JSON en un {@link RoadGraph}. Si una calle no trae {@code length} se usa la
 * distancia euclidiana entre sus intersecciones.
 */
public final class MapLoader {

    private static final String BASE_PATH = "/com/astra/";
    private static final Gson GSON = new Gson();

    public record LoadedMap(String name, RoadGraph graph) {
    }

    private record MapDto(String name, List<IntersectionDto> intersections, List<RoadDto> roads) {
    }

    private record IntersectionDto(String id, Double x, Double y) {
    }

    private record RoadDto(String id, String from, String to, Double speedLimit, Integer lanes, Boolean twoWay,
            Double length) {
    }

    private MapLoader() {
    }

    /** Rutas relativas se resuelven desde {@code /com/astra/}, por ejemplo {@code maps/city-3x3.json}. */
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
        return new LoadedMap(require(dto.name(), "map name"), buildGraph(dto));
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
            try {
                var intersection = new Intersection(id, x, y);
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
