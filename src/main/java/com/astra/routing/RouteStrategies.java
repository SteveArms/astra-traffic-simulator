package com.astra.routing;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import java.util.function.Supplier;

/** Fábrica de estrategias por nombre ({@code SHORTEST}, {@code LEAST_CONGESTED}, {@code RANDOM}). */
public final class RouteStrategies {

    public static final List<String> NAMES =
            List.of(ShortestDistanceStrategy.NAME, LeastCongestedStrategy.NAME, RandomRouteStrategy.NAME);

    private RouteStrategies() {
    }

    /** Normaliza el nombre (sin espacios, mayúsculas). @throws IllegalArgumentException si no existe */
    public static String canonicalName(String name) {
        Objects.requireNonNull(name, "name");
        String canonical = name.strip().toUpperCase(Locale.ROOT);
        if (!NAMES.contains(canonical)) {
            throw new IllegalArgumentException("Unknown route strategy: " + name);
        }
        return canonical;
    }

    /**
     * @param settings normalmente el {@code SimConfig}
     * @throws IllegalArgumentException si el nombre no corresponde a ninguna estrategia
     */
    public static RouteStrategy byName(String name, Supplier<RoadOccupancy> occupancy, RoutingSettings settings,
            Random rnd) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(settings, "settings");
        return switch (name.strip().toUpperCase(Locale.ROOT)) {
            case ShortestDistanceStrategy.NAME -> new ShortestDistanceStrategy();
            case LeastCongestedStrategy.NAME -> new LeastCongestedStrategy(occupancy, settings.congestionFactor());
            case RandomRouteStrategy.NAME -> new RandomRouteStrategy(rnd, settings.randomJitter());
            default -> throw new IllegalArgumentException("Unknown route strategy: " + name);
        };
    }
}
