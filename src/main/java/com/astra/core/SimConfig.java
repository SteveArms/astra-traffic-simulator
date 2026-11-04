package com.astra.core;

import com.astra.model.VehicleKind;
import com.astra.model.VehicleSpec;
import com.astra.model.VehicleSpecs;
import com.astra.routing.LeastCongestedStrategy;
import com.astra.routing.RandomRouteStrategy;
import com.astra.routing.RoutingSettings;
import com.astra.routing.ShortestDistanceStrategy;
import com.astra.traffic.LightTimings;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/**
 * Configuración leída de {@code config.properties}. Claves ausentes usan su valor por defecto;
 * valores inválidos lanzan {@link IllegalArgumentException} con el nombre de la clave.
 * Thread-safe por ser inmutable.
 */
public final class SimConfig implements LightTimings, RoutingSettings, VehicleSpecs {

    public static final String RESOURCE = "/com/astra/config.properties";

    static final String TICK_MILLIS = "engine.tickMillis";
    static final String GREEN_SECONDS = "lights.greenSeconds";
    static final String YELLOW_SECONDS = "lights.yellowSeconds";
    static final String ALL_RED_SECONDS = "lights.allRedSeconds";
    static final String SMART_QUEUE_THRESHOLD = "smart.queueThreshold";
    static final String SMART_MAX_EXTENSION_SECONDS = "smart.maxExtensionSeconds";
    static final String SPAWN_INTERVAL_MILLIS = "spawn.intervalMillis";
    static final String VEHICLE_MAX_COUNT = "vehicle.maxCount";
    static final String CAR_MAX_SPEED = "vehicle.car.maxSpeed";
    static final String CAR_ACCELERATION = "vehicle.car.acceleration";
    static final String CAR_DECELERATION = "vehicle.car.deceleration";
    static final String CAR_LENGTH = "vehicle.car.length";
    static final String BUS_MAX_SPEED = "vehicle.bus.maxSpeed";
    static final String BUS_ACCELERATION = "vehicle.bus.acceleration";
    static final String BUS_DECELERATION = "vehicle.bus.deceleration";
    static final String BUS_LENGTH = "vehicle.bus.length";
    static final String AMBULANCE_MAX_SPEED = "vehicle.ambulance.maxSpeed";
    static final String AMBULANCE_ACCELERATION = "vehicle.ambulance.acceleration";
    static final String AMBULANCE_DECELERATION = "vehicle.ambulance.deceleration";
    static final String AMBULANCE_LENGTH = "vehicle.ambulance.length";
    static final String MIN_GAP = "vehicle.minGap";
    static final String STOP_LINE_OFFSET = "intersection.stopLineOffset";
    static final String INTERSECTION_CAPACITY = "intersection.capacity";
    static final String BOX_LENGTH = "intersection.boxLength";
    static final String APPROACH_MARGIN = "intersection.approachMargin";
    static final String ROUTING_CONGESTION_FACTOR = "routing.congestionFactor";
    static final String ROUTING_RANDOM_JITTER = "routing.randomJitter";
    static final String EMERGENCY_PREEMPT_DISTANCE = "emergency.preemptDistance";
    static final String EMERGENCY_MAX_SECONDS = "emergency.maxSeconds";
    static final String SPAWN_SEED = "spawn.seed";
    static final String SPAWN_WEIGHT_CAR = "spawn.weight.car";
    static final String SPAWN_WEIGHT_BUS = "spawn.weight.bus";
    static final String SPAWN_WEIGHT_AMBULANCE = "spawn.weight.ambulance";
    static final String ROUTING_WEIGHT_SHORTEST = "routing.weight.shortest";
    static final String ROUTING_WEIGHT_LEAST_CONGESTED = "routing.weight.leastCongested";
    static final String ROUTING_WEIGHT_RANDOM = "routing.weight.random";
    static final String MAP_FILE = "map.file";
    static final String LOG_FILE = "log.file";

    // Deben coincidir con config.properties.
    private static final long DEFAULT_TICK_MILLIS = 50;
    private static final long DEFAULT_GREEN_SECONDS = 8;
    private static final long DEFAULT_YELLOW_SECONDS = 2;
    private static final long DEFAULT_ALL_RED_SECONDS = 1;
    private static final int DEFAULT_SMART_QUEUE_THRESHOLD = 5;
    private static final long DEFAULT_SMART_MAX_EXTENSION_SECONDS = 6;
    private static final long DEFAULT_SPAWN_INTERVAL_MILLIS = 1500;
    private static final int DEFAULT_VEHICLE_MAX_COUNT = 150;
    private static final double DEFAULT_CAR_MAX_SPEED = 14;
    private static final double DEFAULT_CAR_ACCELERATION = 3;
    private static final double DEFAULT_CAR_DECELERATION = 6;
    private static final double DEFAULT_CAR_LENGTH = 12;
    private static final double DEFAULT_BUS_MAX_SPEED = 9;
    private static final double DEFAULT_BUS_ACCELERATION = 1.5;
    private static final double DEFAULT_BUS_DECELERATION = 4;
    private static final double DEFAULT_BUS_LENGTH = 20;
    private static final double DEFAULT_AMBULANCE_MAX_SPEED = 20;
    private static final double DEFAULT_AMBULANCE_ACCELERATION = 4;
    private static final double DEFAULT_AMBULANCE_DECELERATION = 8;
    private static final double DEFAULT_AMBULANCE_LENGTH = 14;
    private static final double DEFAULT_MIN_GAP = 10;
    private static final double DEFAULT_STOP_LINE_OFFSET = 14;
    private static final int DEFAULT_INTERSECTION_CAPACITY = 2;
    private static final double DEFAULT_BOX_LENGTH = 16;
    private static final double DEFAULT_APPROACH_MARGIN = 2;
    private static final double DEFAULT_ROUTING_CONGESTION_FACTOR = 2.0;
    private static final double DEFAULT_ROUTING_RANDOM_JITTER = 0.6;
    private static final double DEFAULT_EMERGENCY_PREEMPT_DISTANCE = 80;
    private static final long DEFAULT_EMERGENCY_MAX_SECONDS = 30;
    private static final long DEFAULT_SPAWN_SEED = 42;
    private static final double DEFAULT_SPAWN_WEIGHT_CAR = 0.75;
    private static final double DEFAULT_SPAWN_WEIGHT_BUS = 0.20;
    private static final double DEFAULT_SPAWN_WEIGHT_AMBULANCE = 0.05;
    private static final double DEFAULT_ROUTING_WEIGHT_SHORTEST = 0.5;
    private static final double DEFAULT_ROUTING_WEIGHT_LEAST_CONGESTED = 0.3;
    private static final double DEFAULT_ROUTING_WEIGHT_RANDOM = 0.2;
    private static final String DEFAULT_MAP_FILE = "maps/city-3x3.json";
    private static final String DEFAULT_LOG_FILE = "logs/astra.log";

    private final Duration tickMillis;
    private final Duration greenSeconds;
    private final Duration yellowSeconds;
    private final Duration allRedSeconds;
    private final int smartQueueThreshold;
    private final Duration smartMaxExtensionSeconds;
    private final Duration spawnIntervalMillis;
    private final int vehicleMaxCount;
    private final double carMaxSpeed;
    private final double carAcceleration;
    private final double carDeceleration;
    private final VehicleSpec carSpec;
    private final VehicleSpec busSpec;
    private final VehicleSpec ambulanceSpec;
    private final double minGap;
    private final double stopLineOffset;
    private final int intersectionCapacity;
    private final double boxLength;
    private final double approachMargin;
    private final double routingCongestionFactor;
    private final double routingRandomJitter;
    private final double emergencyPreemptDistance;
    private final Duration emergencyMaxSeconds;
    private final long spawnSeed;
    private final Map<VehicleKind, Double> spawnWeights;
    private final Map<String, Double> routingWeights;
    private final String mapFile;
    private final String logFile;

    private SimConfig(Properties p) {
        tickMillis = Duration.ofMillis(positiveLong(p, TICK_MILLIS, DEFAULT_TICK_MILLIS));
        greenSeconds = Duration.ofSeconds(positiveLong(p, GREEN_SECONDS, DEFAULT_GREEN_SECONDS));
        yellowSeconds = Duration.ofSeconds(positiveLong(p, YELLOW_SECONDS, DEFAULT_YELLOW_SECONDS));
        allRedSeconds = Duration.ofSeconds(positiveLong(p, ALL_RED_SECONDS, DEFAULT_ALL_RED_SECONDS));
        smartQueueThreshold = positiveInt(p, SMART_QUEUE_THRESHOLD, DEFAULT_SMART_QUEUE_THRESHOLD);
        smartMaxExtensionSeconds = Duration.ofSeconds(
                positiveLong(p, SMART_MAX_EXTENSION_SECONDS, DEFAULT_SMART_MAX_EXTENSION_SECONDS));
        spawnIntervalMillis = Duration.ofMillis(
                positiveLong(p, SPAWN_INTERVAL_MILLIS, DEFAULT_SPAWN_INTERVAL_MILLIS));
        vehicleMaxCount = positiveInt(p, VEHICLE_MAX_COUNT, DEFAULT_VEHICLE_MAX_COUNT);
        carMaxSpeed = positiveDouble(p, CAR_MAX_SPEED, DEFAULT_CAR_MAX_SPEED);
        carAcceleration = positiveDouble(p, CAR_ACCELERATION, DEFAULT_CAR_ACCELERATION);
        carDeceleration = positiveDouble(p, CAR_DECELERATION, DEFAULT_CAR_DECELERATION);
        carSpec = new VehicleSpec(carMaxSpeed, carAcceleration, carDeceleration,
                positiveDouble(p, CAR_LENGTH, DEFAULT_CAR_LENGTH));
        busSpec = new VehicleSpec(positiveDouble(p, BUS_MAX_SPEED, DEFAULT_BUS_MAX_SPEED),
                positiveDouble(p, BUS_ACCELERATION, DEFAULT_BUS_ACCELERATION),
                positiveDouble(p, BUS_DECELERATION, DEFAULT_BUS_DECELERATION),
                positiveDouble(p, BUS_LENGTH, DEFAULT_BUS_LENGTH));
        ambulanceSpec = new VehicleSpec(positiveDouble(p, AMBULANCE_MAX_SPEED, DEFAULT_AMBULANCE_MAX_SPEED),
                positiveDouble(p, AMBULANCE_ACCELERATION, DEFAULT_AMBULANCE_ACCELERATION),
                positiveDouble(p, AMBULANCE_DECELERATION, DEFAULT_AMBULANCE_DECELERATION),
                positiveDouble(p, AMBULANCE_LENGTH, DEFAULT_AMBULANCE_LENGTH));
        minGap = positiveDouble(p, MIN_GAP, DEFAULT_MIN_GAP);
        stopLineOffset = positiveDouble(p, STOP_LINE_OFFSET, DEFAULT_STOP_LINE_OFFSET);
        intersectionCapacity = positiveInt(p, INTERSECTION_CAPACITY, DEFAULT_INTERSECTION_CAPACITY);
        boxLength = positiveDouble(p, BOX_LENGTH, DEFAULT_BOX_LENGTH);
        approachMargin = positiveDouble(p, APPROACH_MARGIN, DEFAULT_APPROACH_MARGIN);
        routingCongestionFactor = positiveDouble(p, ROUTING_CONGESTION_FACTOR, DEFAULT_ROUTING_CONGESTION_FACTOR);
        routingRandomJitter = positiveDouble(p, ROUTING_RANDOM_JITTER, DEFAULT_ROUTING_RANDOM_JITTER);
        emergencyPreemptDistance = positiveDouble(p, EMERGENCY_PREEMPT_DISTANCE, DEFAULT_EMERGENCY_PREEMPT_DISTANCE);
        emergencyMaxSeconds = Duration.ofSeconds(
                positiveLong(p, EMERGENCY_MAX_SECONDS, DEFAULT_EMERGENCY_MAX_SECONDS));
        spawnSeed = positiveLong(p, SPAWN_SEED, DEFAULT_SPAWN_SEED);
        var kinds = new EnumMap<VehicleKind, Double>(VehicleKind.class);
        kinds.put(VehicleKind.CAR, nonNegativeDouble(p, SPAWN_WEIGHT_CAR, DEFAULT_SPAWN_WEIGHT_CAR));
        kinds.put(VehicleKind.BUS, nonNegativeDouble(p, SPAWN_WEIGHT_BUS, DEFAULT_SPAWN_WEIGHT_BUS));
        kinds.put(VehicleKind.AMBULANCE, nonNegativeDouble(p, SPAWN_WEIGHT_AMBULANCE, DEFAULT_SPAWN_WEIGHT_AMBULANCE));
        spawnWeights = Collections.unmodifiableMap(requireSomeWeight(kinds, "spawn.weight.*"));
        // Orden fijo de iteración: la selección ponderada debe ser reproducible con la semilla.
        var strategies = new LinkedHashMap<String, Double>();
        strategies.put(ShortestDistanceStrategy.NAME,
                nonNegativeDouble(p, ROUTING_WEIGHT_SHORTEST, DEFAULT_ROUTING_WEIGHT_SHORTEST));
        strategies.put(LeastCongestedStrategy.NAME,
                nonNegativeDouble(p, ROUTING_WEIGHT_LEAST_CONGESTED, DEFAULT_ROUTING_WEIGHT_LEAST_CONGESTED));
        strategies.put(RandomRouteStrategy.NAME,
                nonNegativeDouble(p, ROUTING_WEIGHT_RANDOM, DEFAULT_ROUTING_WEIGHT_RANDOM));
        routingWeights = Collections.unmodifiableMap(requireSomeWeight(strategies, "routing.weight.*"));
        mapFile = nonBlank(p, MAP_FILE, DEFAULT_MAP_FILE);
        logFile = nonBlank(p, LOG_FILE, DEFAULT_LOG_FILE);
    }

    public static SimConfig load() {
        try (InputStream in = SimConfig.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Configuration resource not found: " + RESOURCE);
            }
            var props = new Properties();
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                props.load(reader);
            }
            return from(props);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to read " + RESOURCE, e);
        }
    }

    public static SimConfig from(Properties p) {
        Objects.requireNonNull(p, "properties");
        return new SimConfig(p);
    }

    public Duration tickMillis() {
        return tickMillis;
    }

    @Override
    public Duration greenSeconds() {
        return greenSeconds;
    }

    @Override
    public Duration yellowSeconds() {
        return yellowSeconds;
    }

    @Override
    public Duration allRedSeconds() {
        return allRedSeconds;
    }

    @Override
    public Duration emergencyMaxSeconds() {
        return emergencyMaxSeconds;
    }

    public int smartQueueThreshold() {
        return smartQueueThreshold;
    }

    public Duration smartMaxExtensionSeconds() {
        return smartMaxExtensionSeconds;
    }

    public Duration spawnIntervalMillis() {
        return spawnIntervalMillis;
    }

    public int vehicleMaxCount() {
        return vehicleMaxCount;
    }

    public double carMaxSpeed() {
        return carMaxSpeed;
    }

    public double carAcceleration() {
        return carAcceleration;
    }

    public long spawnSeed() {
        return spawnSeed;
    }

    /** Peso relativo de cada tipo, en el orden de {@link VehicleKind}; la suma es &gt; 0. */
    public Map<VehicleKind, Double> spawnWeights() {
        return spawnWeights;
    }

    /** Peso relativo de cada estrategia por su {@code name()}, en orden fijo; la suma es &gt; 0. */
    public Map<String, Double> routingWeights() {
        return routingWeights;
    }

    public double carDeceleration() {
        return carDeceleration;
    }

    @Override
    public VehicleSpec specFor(VehicleKind kind) {
        return switch (kind) {
            case CAR -> carSpec;
            case BUS -> busSpec;
            case AMBULANCE -> ambulanceSpec;
        };
    }

    public double minGap() {
        return minGap;
    }

    public double stopLineOffset() {
        return stopLineOffset;
    }

    public int intersectionCapacity() {
        return intersectionCapacity;
    }

    public double boxLength() {
        return boxLength;
    }

    public double approachMargin() {
        return approachMargin;
    }

    @Override
    public double congestionFactor() {
        return routingCongestionFactor;
    }

    @Override
    public double randomJitter() {
        return routingRandomJitter;
    }

    public double emergencyPreemptDistance() {
        return emergencyPreemptDistance;
    }

    public String mapFile() {
        return mapFile;
    }

    public String logFile() {
        return logFile;
    }

    private static double positiveDouble(Properties p, String key, double defaultValue) {
        String raw = p.getProperty(key);
        if (raw == null) {
            return defaultValue;
        }
        double value;
        try {
            value = Double.parseDouble(raw.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid value for '" + key + "': '" + raw + "' is not a number", e);
        }
        if (!(value > 0) || !Double.isFinite(value)) {
            throw new IllegalArgumentException("Invalid value for '" + key + "': " + value + " must be > 0");
        }
        return value;
    }

    private static double nonNegativeDouble(Properties p, String key, double defaultValue) {
        String raw = p.getProperty(key);
        if (raw == null) {
            return defaultValue;
        }
        double value;
        try {
            value = Double.parseDouble(raw.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid value for '" + key + "': '" + raw + "' is not a number", e);
        }
        if (!(value >= 0) || !Double.isFinite(value)) {
            throw new IllegalArgumentException("Invalid value for '" + key + "': " + value + " must be >= 0");
        }
        return value;
    }

    private static <K, M extends Map<K, Double>> M requireSomeWeight(M weights, String keys) {
        double total = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        if (!(total > 0)) {
            throw new IllegalArgumentException("Invalid values for '" + keys + "': at least one weight must be > 0");
        }
        return weights;
    }

    private static long positiveLong(Properties p, String key, long defaultValue) {
        String raw = p.getProperty(key);
        if (raw == null) {
            return defaultValue;
        }
        long value;
        try {
            value = Long.parseLong(raw.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid value for '" + key + "': '" + raw + "' is not an integer", e);
        }
        if (value <= 0) {
            throw new IllegalArgumentException("Invalid value for '" + key + "': " + value + " must be > 0");
        }
        return value;
    }

    private static int positiveInt(Properties p, String key, int defaultValue) {
        long value = positiveLong(p, key, defaultValue);
        if (value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid value for '" + key + "': " + value + " is too large");
        }
        return (int) value;
    }

    private static String nonBlank(Properties p, String key, String defaultValue) {
        String raw = p.getProperty(key);
        if (raw == null) {
            return defaultValue;
        }
        if (raw.isBlank()) {
            throw new IllegalArgumentException("Invalid value for '" + key + "': must not be blank");
        }
        return raw.strip();
    }
}
