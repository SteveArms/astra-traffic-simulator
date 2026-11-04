package com.astra.core;

import com.astra.routing.RoutingSettings;
import com.astra.traffic.LightTimings;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.Properties;

/**
 * Configuración leída de {@code config.properties}. Claves ausentes usan su valor por defecto;
 * valores inválidos lanzan {@link IllegalArgumentException} con el nombre de la clave.
 * Thread-safe por ser inmutable.
 */
public final class SimConfig implements LightTimings, RoutingSettings {

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
    static final String MIN_GAP = "vehicle.minGap";
    static final String STOP_LINE_OFFSET = "intersection.stopLineOffset";
    static final String INTERSECTION_CAPACITY = "intersection.capacity";
    static final String BOX_LENGTH = "intersection.boxLength";
    static final String APPROACH_MARGIN = "intersection.approachMargin";
    static final String ROUTING_CONGESTION_FACTOR = "routing.congestionFactor";
    static final String ROUTING_RANDOM_JITTER = "routing.randomJitter";
    static final String DEMO_CAR_COUNT = "demo.carCount";
    static final String DEMO_SEED = "demo.seed";
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
    private static final double DEFAULT_MIN_GAP = 10;
    private static final double DEFAULT_STOP_LINE_OFFSET = 14;
    private static final int DEFAULT_INTERSECTION_CAPACITY = 2;
    private static final double DEFAULT_BOX_LENGTH = 16;
    private static final double DEFAULT_APPROACH_MARGIN = 2;
    private static final double DEFAULT_ROUTING_CONGESTION_FACTOR = 2.0;
    private static final double DEFAULT_ROUTING_RANDOM_JITTER = 0.6;
    private static final int DEFAULT_DEMO_CAR_COUNT = 4;
    private static final long DEFAULT_DEMO_SEED = 42;
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
    private final double minGap;
    private final double stopLineOffset;
    private final int intersectionCapacity;
    private final double boxLength;
    private final double approachMargin;
    private final double routingCongestionFactor;
    private final double routingRandomJitter;
    private final int demoCarCount;
    private final long demoSeed;
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
        minGap = positiveDouble(p, MIN_GAP, DEFAULT_MIN_GAP);
        stopLineOffset = positiveDouble(p, STOP_LINE_OFFSET, DEFAULT_STOP_LINE_OFFSET);
        intersectionCapacity = positiveInt(p, INTERSECTION_CAPACITY, DEFAULT_INTERSECTION_CAPACITY);
        boxLength = positiveDouble(p, BOX_LENGTH, DEFAULT_BOX_LENGTH);
        approachMargin = positiveDouble(p, APPROACH_MARGIN, DEFAULT_APPROACH_MARGIN);
        routingCongestionFactor = positiveDouble(p, ROUTING_CONGESTION_FACTOR, DEFAULT_ROUTING_CONGESTION_FACTOR);
        routingRandomJitter = positiveDouble(p, ROUTING_RANDOM_JITTER, DEFAULT_ROUTING_RANDOM_JITTER);
        demoCarCount = positiveInt(p, DEMO_CAR_COUNT, DEFAULT_DEMO_CAR_COUNT);
        demoSeed = positiveLong(p, DEMO_SEED, DEFAULT_DEMO_SEED);
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

    public long demoSeed() {
        return demoSeed;
    }

    public double carDeceleration() {
        return carDeceleration;
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

    public int demoCarCount() {
        return demoCarCount;
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
