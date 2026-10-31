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
 * Configuración de la simulación leída de {@code config.properties}.
 *
 * <p>Si falta una clave se usa su valor por defecto; si un valor numérico no es un entero
 * positivo, o un texto está vacío, se lanza {@link IllegalArgumentException} con el nombre
 * de la clave.
 *
 * <p>Es <b>thread-safe</b> por ser inmutable: todos sus campos son {@code final} y de tipos
 * inmutables, así que una instancia puede compartirse sin sincronización entre el FX Thread,
 * {@code astra-engine}, los workers y {@code astra-lights}.
 */
public final class SimConfig {

    /** Ruta del archivo de configuración dentro del classpath. */
    public static final String RESOURCE = "/com/astra/config.properties";

    static final String TICK_MILLIS = "engine.tickMillis";
    static final String GREEN_SECONDS = "lights.greenSeconds";
    static final String YELLOW_SECONDS = "lights.yellowSeconds";
    static final String RED_SECONDS = "lights.redSeconds";
    static final String SMART_QUEUE_THRESHOLD = "smart.queueThreshold";
    static final String SMART_MAX_EXTENSION_SECONDS = "smart.maxExtensionSeconds";
    static final String SPAWN_INTERVAL_MILLIS = "spawn.intervalMillis";
    static final String VEHICLE_MAX_COUNT = "vehicle.maxCount";
    static final String MAP_FILE = "map.file";
    static final String LOG_FILE = "log.file";

    // Valores por defecto: deben coincidir con config.properties y con AGENTS.md (sección 8).
    private static final long DEFAULT_TICK_MILLIS = 50;
    private static final long DEFAULT_GREEN_SECONDS = 8;
    private static final long DEFAULT_YELLOW_SECONDS = 2;
    private static final long DEFAULT_RED_SECONDS = 10;
    private static final int DEFAULT_SMART_QUEUE_THRESHOLD = 5;
    private static final long DEFAULT_SMART_MAX_EXTENSION_SECONDS = 6;
    private static final long DEFAULT_SPAWN_INTERVAL_MILLIS = 1500;
    private static final int DEFAULT_VEHICLE_MAX_COUNT = 150;
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
        mapFile = nonBlank(p, MAP_FILE, DEFAULT_MAP_FILE);
        logFile = nonBlank(p, LOG_FILE, DEFAULT_LOG_FILE);
    }

    /**
     * Carga la configuración desde {@value #RESOURCE} en el classpath (UTF-8).
     *
     * @throws IllegalStateException    si el recurso no existe
     * @throws UncheckedIOException     si falla la lectura
     * @throws IllegalArgumentException si algún valor es inválido
     */
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

    /**
     * Construye la configuración a partir de un {@link Properties} ya cargado (útil en tests).
     *
     * @throws IllegalArgumentException si algún valor es inválido
     */
    public static SimConfig from(Properties p) {
        Objects.requireNonNull(p, "properties");
        return new SimConfig(p);
    }

    /** Duración de un tick del motor ({@code engine.tickMillis}). */
    public Duration tickMillis() {
        return tickMillis;
    }

    /** Duración de la luz verde ({@code lights.greenSeconds}). */
    public Duration greenSeconds() {
        return greenSeconds;
    }

    /** Duración de la luz amarilla ({@code lights.yellowSeconds}). */
    public Duration yellowSeconds() {
        return yellowSeconds;
    }

    /** Duración de la luz roja ({@code lights.redSeconds}). */
    public Duration redSeconds() {
        return redSeconds;
    }

    /** Vehículos en cola a partir de los cuales el semáforo inteligente extiende el verde. */
    public int smartQueueThreshold() {
        return smartQueueThreshold;
    }

    /** Extensión máxima del verde en el semáforo inteligente ({@code smart.maxExtensionSeconds}). */
    public Duration smartMaxExtensionSeconds() {
        return smartMaxExtensionSeconds;
    }

    /** Intervalo entre apariciones de vehículos ({@code spawn.intervalMillis}). */
    public Duration spawnIntervalMillis() {
        return spawnIntervalMillis;
    }

    /** Número máximo de vehículos simultáneos ({@code vehicle.maxCount}). */
    public int vehicleMaxCount() {
        return vehicleMaxCount;
    }

    /** Ruta del mapa, relativa a {@code /com/astra/} en el classpath ({@code map.file}). */
    public String mapFile() {
        return mapFile;
    }

    /** Ruta del archivo de log en disco ({@code log.file}). */
    public String logFile() {
        return logFile;
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
