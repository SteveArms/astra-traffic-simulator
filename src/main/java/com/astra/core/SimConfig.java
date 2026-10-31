package com.astra.core;

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
public final class SimConfig {

    public static final String RESOURCE = "/com/astra/config.properties";

    static final String TICK_MILLIS = "engine.tickMillis";
    static final String GREEN_SECONDS = "lights.greenSeconds";
    static final String YELLOW_SECONDS = "lights.yellowSeconds";
    static final String RED_SECONDS = "lights.redSeconds";
    static final String SMART_QUEUE_THRESHOLD = "smart.queueThreshold";
    static final String SMART_MAX_EXTENSION_SECONDS = "smart.maxExtensionSeconds";
    static final String SPAWN_INTERVAL_MILLIS = "spawn.intervalMillis";
    static final String VEHICLE_MAX_COUNT = "vehicle.maxCount";
    static final String CAR_MAX_SPEED = "vehicle.car.maxSpeed";
    static final String CAR_ACCELERATION = "vehicle.car.acceleration";
    static final String DEMO_CAR_COUNT = "demo.carCount";
    static final String DEMO_SEED = "demo.seed";
    static final String MAP_FILE = "map.file";
    static final String LOG_FILE = "log.file";

    // Deben coincidir con config.properties.
    private static final long DEFAULT_TICK_MILLIS = 50;
    private static final long DEFAULT_GREEN_SECONDS = 8;
    private static final long DEFAULT_YELLOW_SECONDS = 2;
    private static final long DEFAULT_RED_SECONDS = 10;
    private static final int DEFAULT_SMART_QUEUE_THRESHOLD = 5;
    private static final long DEFAULT_SMART_MAX_EXTENSION_SECONDS = 6;
    private static final long DEFAULT_SPAWN_INTERVAL_MILLIS = 1500;
    private static final int DEFAULT_VEHICLE_MAX_COUNT = 150;
    private static final double DEFAULT_CAR_MAX_SPEED = 14;
    private static final double DEFAULT_CAR_ACCELERATION = 3;
    private static final int DEFAULT_DEMO_CAR_COUNT = 4;
    private static final long DEFAULT_DEMO_SEED = 42;
    private static final String DEFAULT_MAP_FILE = "maps/city-3x3.json";
    private static final String DEFAULT_LOG_FILE = "logs/astra.log";

    private final Duration tickMillis;
    private final Duration greenSeconds;
    private final Duration yellowSeconds;
    private final Duration redSeconds;
    private final int smartQueueThreshold;
    private final Duration smartMaxExtensionSeconds;
    private final Duration spawnIntervalMillis;
    private final int vehicleMaxCount;
    private final double carMaxSpeed;
    private final double carAcceleration;
    private final int demoCarCount;
    private final long demoSeed;
    private final String mapFile;
    private final String logFile;

    private SimConfig(Properties p) {
        tickMillis = Duration.ofMillis(positiveLong(p, TICK_MILLIS, DEFAULT_TICK_MILLIS));
        greenSeconds = Duration.ofSeconds(positiveLong(p, GREEN_SECONDS, DEFAULT_GREEN_SECONDS));
        yellowSeconds = Duration.ofSeconds(positiveLong(p, YELLOW_SECONDS, DEFAULT_YELLOW_SECONDS));
        redSeconds = Duration.ofSeconds(positiveLong(p, RED_SECONDS, DEFAULT_RED_SECONDS));
        smartQueueThreshold = positiveInt(p, SMART_QUEUE_THRESHOLD, DEFAULT_SMART_QUEUE_THRESHOLD);
        smartMaxExtensionSeconds = Duration.ofSeconds(
                positiveLong(p, SMART_MAX_EXTENSION_SECONDS, DEFAULT_SMART_MAX_EXTENSION_SECONDS));
        spawnIntervalMillis = Duration.ofMillis(
                positiveLong(p, SPAWN_INTERVAL_MILLIS, DEFAULT_SPAWN_INTERVAL_MILLIS));
        vehicleMaxCount = positiveInt(p, VEHICLE_MAX_COUNT, DEFAULT_VEHICLE_MAX_COUNT);
        carMaxSpeed = positiveDouble(p, CAR_MAX_SPEED, DEFAULT_CAR_MAX_SPEED);
        carAcceleration = positiveDouble(p, CAR_ACCELERATION, DEFAULT_CAR_ACCELERATION);
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

    public Duration greenSeconds() {
        return greenSeconds;
    }

    public Duration yellowSeconds() {
        return yellowSeconds;
    }

    public Duration redSeconds() {
        return redSeconds;
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
