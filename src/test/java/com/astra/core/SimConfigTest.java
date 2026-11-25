package com.astra.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.astra.model.VehicleKind;
import com.astra.model.VehicleSpec;
import com.astra.util.SimLogger;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SimConfigTest {

    @Test
    void loadReadsRealConfigFile() {
        SimConfig config = SimConfig.load();

        assertEquals(Duration.ofMillis(50), config.tickMillis());
        assertTrue(config.engineParallel());
        assertEquals(4, config.engineWorkers());
        assertEquals(Duration.ofSeconds(8), config.greenSeconds());
        assertEquals(Duration.ofSeconds(2), config.yellowSeconds());
        assertEquals(Duration.ofSeconds(1), config.allRedSeconds());
        assertEquals(5, config.smartQueueThreshold());
        assertEquals(Duration.ofSeconds(6), config.smartMaxExtensionSeconds());
        assertEquals(Duration.ofSeconds(2), config.smartExtensionStepSeconds());
        assertEquals(120, config.smartQueueRadius());
        assertStats(config);
        assertEquals(Duration.ofMillis(6_000), config.spawnIntervalMillis());
        assertEquals(4, config.spawnBatchSize());
        assertEquals(150, config.vehicleMaxCount());
        assertEquals(14, config.carMaxSpeed());
        assertEquals(3, config.carAcceleration());
        assertEquals(6, config.carDeceleration());
        assertEquals(10, config.minGap());
        assertEquals(14, config.stopLineOffset());
        assertEquals(2, config.intersectionCapacity());
        assertEquals(16, config.boxLength());
        assertEquals(2, config.approachMargin());
        assertSpecs(config);
        assertEquals(2.0, config.congestionFactor());
        assertEquals(0.6, config.randomJitter());
        assertEquals(80, config.emergencyPreemptDistance());
        assertEquals(Duration.ofSeconds(30), config.emergencyMaxSeconds());
        assertEquals(42, config.spawnSeed());
        assertEquals(Map.of(VehicleKind.CAR, 0.75, VehicleKind.BUS, 0.20, VehicleKind.AMBULANCE, 0.05),
                config.spawnWeights());
        assertEquals(Map.of("SHORTEST", 0.5, "LEAST_CONGESTED", 0.3, "RANDOM", 0.2), config.routingWeights());
        assertEquals("maps/city-3x3.json", config.mapFile());
        assertEquals(Duration.ofMillis(5), config.latencyMin());
        assertEquals(Duration.ofMillis(40), config.latencyMax());
        assertEquals(7, config.networkSeed());
        assertEquals(MutexMode.SEMAPHORE, config.mutexMode());
        assertEquals(60, config.grantTimeoutTicks());
        assertEquals("logs/astra.log", config.logFile());
        assertTrue(config.logConsole());
        assertEquals(SimLogger.Level.INFO, config.logLevel());
    }

    @Test
    void missingKeysUseDefaults() {
        SimConfig config = SimConfig.from(new Properties());

        assertEquals(Duration.ofMillis(50), config.tickMillis());
        assertTrue(config.engineParallel());
        assertEquals(4, config.engineWorkers());
        assertEquals(Duration.ofSeconds(8), config.greenSeconds());
        assertEquals(Duration.ofSeconds(2), config.yellowSeconds());
        assertEquals(Duration.ofSeconds(1), config.allRedSeconds());
        assertEquals(5, config.smartQueueThreshold());
        assertEquals(Duration.ofSeconds(6), config.smartMaxExtensionSeconds());
        assertEquals(Duration.ofSeconds(2), config.smartExtensionStepSeconds());
        assertEquals(120, config.smartQueueRadius());
        assertStats(config);
        assertEquals(Duration.ofMillis(300), config.spawnIntervalMillis());
        assertEquals(1, config.spawnBatchSize());
        assertEquals(150, config.vehicleMaxCount());
        assertEquals(14, config.carMaxSpeed());
        assertEquals(3, config.carAcceleration());
        assertEquals(6, config.carDeceleration());
        assertEquals(10, config.minGap());
        assertEquals(14, config.stopLineOffset());
        assertEquals(2, config.intersectionCapacity());
        assertEquals(16, config.boxLength());
        assertEquals(2, config.approachMargin());
        assertSpecs(config);
        assertEquals(2.0, config.congestionFactor());
        assertEquals(0.6, config.randomJitter());
        assertEquals(80, config.emergencyPreemptDistance());
        assertEquals(Duration.ofSeconds(30), config.emergencyMaxSeconds());
        assertEquals(42, config.spawnSeed());
        assertEquals(Map.of(VehicleKind.CAR, 0.75, VehicleKind.BUS, 0.20, VehicleKind.AMBULANCE, 0.05),
                config.spawnWeights());
        assertEquals(Map.of("SHORTEST", 0.5, "LEAST_CONGESTED", 0.3, "RANDOM", 0.2), config.routingWeights());
        assertEquals("maps/city-3x3.json", config.mapFile());
        assertEquals(Duration.ofMillis(5), config.latencyMin());
        assertEquals(Duration.ofMillis(40), config.latencyMax());
        assertEquals(7, config.networkSeed());
        assertEquals(MutexMode.SEMAPHORE, config.mutexMode());
        assertEquals(60, config.grantTimeoutTicks());
        assertEquals("logs/astra.log", config.logFile());
        assertTrue(config.logConsole());
        assertEquals(SimLogger.Level.INFO, config.logLevel());
    }

    @Test
    void presentKeyOverridesDefault() {
        var props = new Properties();
        props.setProperty(SimConfig.GREEN_SECONDS, "12");

        SimConfig config = SimConfig.from(props);

        assertEquals(Duration.ofSeconds(12), config.greenSeconds());
        assertEquals(Duration.ofSeconds(2), config.yellowSeconds());
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "", "1.5", "0", "-3"})
    void invalidNumericValueThrowsWithKeyName(String value) {
        var props = new Properties();
        props.setProperty(SimConfig.TICK_MILLIS, value);

        var e = assertThrows(IllegalArgumentException.class, () -> SimConfig.from(props));
        assertTrue(e.getMessage().contains(SimConfig.TICK_MILLIS), e.getMessage());
    }

    @Test
    void intValueOutOfRangeThrows() {
        var props = new Properties();
        props.setProperty(SimConfig.VEHICLE_MAX_COUNT, "3000000000");

        var e = assertThrows(IllegalArgumentException.class, () -> SimConfig.from(props));
        assertTrue(e.getMessage().contains(SimConfig.VEHICLE_MAX_COUNT), e.getMessage());
    }

    @Test
    void blankTextValueThrowsWithKeyName() {
        var props = new Properties();
        props.setProperty(SimConfig.MAP_FILE, "  ");

        var e = assertThrows(IllegalArgumentException.class, () -> SimConfig.from(props));
        assertTrue(e.getMessage().contains(SimConfig.MAP_FILE), e.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "0", "-2.5", "NaN", "Infinity"})
    void invalidDecimalValueThrowsWithKeyName(String value) {
        var props = new Properties();
        props.setProperty(SimConfig.CAR_MAX_SPEED, value);

        var e = assertThrows(IllegalArgumentException.class, () -> SimConfig.from(props));
        assertTrue(e.getMessage().contains(SimConfig.CAR_MAX_SPEED), e.getMessage());
    }

    @Test
    void decimalValuesAreAccepted() {
        var props = new Properties();
        props.setProperty(SimConfig.CAR_ACCELERATION, "2.5");

        assertEquals(2.5, SimConfig.from(props).carAcceleration());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        SimConfig.ALL_RED_SECONDS, SimConfig.CAR_DECELERATION, SimConfig.MIN_GAP,
        SimConfig.STOP_LINE_OFFSET, SimConfig.INTERSECTION_CAPACITY, SimConfig.BOX_LENGTH,
        SimConfig.APPROACH_MARGIN, SimConfig.ROUTING_CONGESTION_FACTOR, SimConfig.ROUTING_RANDOM_JITTER,
        SimConfig.CAR_LENGTH, SimConfig.BUS_MAX_SPEED, SimConfig.BUS_ACCELERATION, SimConfig.BUS_DECELERATION,
        SimConfig.BUS_LENGTH, SimConfig.AMBULANCE_MAX_SPEED, SimConfig.AMBULANCE_ACCELERATION,
        SimConfig.AMBULANCE_DECELERATION, SimConfig.AMBULANCE_LENGTH, SimConfig.EMERGENCY_PREEMPT_DISTANCE,
        SimConfig.EMERGENCY_MAX_SECONDS, SimConfig.SPAWN_SEED, SimConfig.SMART_EXTENSION_STEP_SECONDS,
        SimConfig.SMART_QUEUE_RADIUS, SimConfig.STATS_THROUGHPUT_WINDOW_SECONDS, SimConfig.STATS_SAMPLE_SECONDS,
        SimConfig.STATS_SERIES_CAPACITY
    })
    void newKeysRejectNonPositiveValues(String key) {
        var props = new Properties();
        props.setProperty(key, "0");

        var e = assertThrows(IllegalArgumentException.class, () -> SimConfig.from(props));
        assertTrue(e.getMessage().contains(key), e.getMessage());
    }

    @Test
    void specForOverridesPerKind() {
        var props = new Properties();
        props.setProperty(SimConfig.BUS_LENGTH, "25");

        SimConfig config = SimConfig.from(props);

        assertEquals(new VehicleSpec(9, 1.5, 4, 25), config.specFor(VehicleKind.BUS));
        assertEquals(new VehicleSpec(14, 3, 6, 12), config.specFor(VehicleKind.CAR));
    }

    @Test
    void weightsKeepAFixedOrderAndAcceptZero() {
        var props = new Properties();
        props.setProperty(SimConfig.SPAWN_WEIGHT_AMBULANCE, "0");

        SimConfig config = SimConfig.from(props);

        assertEquals(List.of(VehicleKind.CAR, VehicleKind.BUS, VehicleKind.AMBULANCE),
                List.copyOf(config.spawnWeights().keySet()));
        assertEquals(List.of("SHORTEST", "LEAST_CONGESTED", "RANDOM"), List.copyOf(config.routingWeights().keySet()));
        assertEquals(0, config.spawnWeights().get(VehicleKind.AMBULANCE));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.1", "abc", "NaN", "Infinity"})
    void invalidWeightThrowsWithKeyName(String value) {
        var props = new Properties();
        props.setProperty(SimConfig.ROUTING_WEIGHT_RANDOM, value);

        var e = assertThrows(IllegalArgumentException.class, () -> SimConfig.from(props));
        assertTrue(e.getMessage().contains(SimConfig.ROUTING_WEIGHT_RANDOM), e.getMessage());
    }

    @Test
    void allZeroWeightsThrow() {
        var props = new Properties();
        props.setProperty(SimConfig.SPAWN_WEIGHT_CAR, "0");
        props.setProperty(SimConfig.SPAWN_WEIGHT_BUS, "0");
        props.setProperty(SimConfig.SPAWN_WEIGHT_AMBULANCE, "0");

        var e = assertThrows(IllegalArgumentException.class, () -> SimConfig.from(props));
        assertTrue(e.getMessage().contains("spawn.weight"), e.getMessage());
    }

    @Test
    void zeroWorkersMeansProcessorsMinusOneButAtLeastOne() {
        var props = new Properties();
        props.setProperty(SimConfig.ENGINE_WORKERS, "0");
        props.setProperty(SimConfig.ENGINE_PARALLEL, "FALSE");

        SimConfig config = SimConfig.from(props);

        assertEquals(Math.max(1, Runtime.getRuntime().availableProcessors() - 1), config.engineWorkers());
        assertEquals(false, config.engineParallel());
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "two", "1.5"})
    void invalidWorkersThrowWithKeyName(String value) {
        var props = new Properties();
        props.setProperty(SimConfig.ENGINE_WORKERS, value);

        var e = assertThrows(IllegalArgumentException.class, () -> SimConfig.from(props));
        assertTrue(e.getMessage().contains(SimConfig.ENGINE_WORKERS), e.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"yes", "1", ""})
    void invalidParallelThrowsWithKeyName(String value) {
        var props = new Properties();
        props.setProperty(SimConfig.ENGINE_PARALLEL, value);

        var e = assertThrows(IllegalArgumentException.class, () -> SimConfig.from(props));
        assertTrue(e.getMessage().contains(SimConfig.ENGINE_PARALLEL), e.getMessage());
    }

    @Test
    void latencyKeysOverrideDefaults() {
        var props = new Properties();
        props.setProperty(SimConfig.DISTRIBUTED_LATENCY_MIN_MILLIS, "0");
        props.setProperty(SimConfig.DISTRIBUTED_LATENCY_MAX_MILLIS, "0");
        props.setProperty(SimConfig.DISTRIBUTED_SEED, "99");

        SimConfig config = SimConfig.from(props);

        assertEquals(Duration.ZERO, config.latencyMin());
        assertEquals(Duration.ZERO, config.latencyMax());
        assertEquals(99, config.networkSeed());
    }

    @Test
    void latencyMaxBelowMinThrowsWithKeyName() {
        var props = new Properties();
        props.setProperty(SimConfig.DISTRIBUTED_LATENCY_MIN_MILLIS, "50");
        props.setProperty(SimConfig.DISTRIBUTED_LATENCY_MAX_MILLIS, "10");

        var e = assertThrows(IllegalArgumentException.class, () -> SimConfig.from(props));
        assertTrue(e.getMessage().contains(SimConfig.DISTRIBUTED_LATENCY_MAX_MILLIS), e.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "abc", ""})
    void invalidLatencyMinThrowsWithKeyName(String value) {
        var props = new Properties();
        props.setProperty(SimConfig.DISTRIBUTED_LATENCY_MIN_MILLIS, value);

        var e = assertThrows(IllegalArgumentException.class, () -> SimConfig.from(props));
        assertTrue(e.getMessage().contains(SimConfig.DISTRIBUTED_LATENCY_MIN_MILLIS), e.getMessage());
    }

    @Test
    void mutexKeysOverrideDefaults() {
        var props = new Properties();
        props.setProperty(SimConfig.DISTRIBUTED_MUTEX, "ricart_agrawala");
        props.setProperty(SimConfig.DISTRIBUTED_GRANT_TIMEOUT_TICKS, "10");

        SimConfig config = SimConfig.from(props);

        assertEquals(MutexMode.RICART_AGRAWALA, config.mutexMode());
        assertEquals(10, config.grantTimeoutTicks());
    }

    @ParameterizedTest
    @ValueSource(strings = {"LAMPORT", "semaforo", ""})
    void invalidMutexModeThrowsWithKeyName(String value) {
        var props = new Properties();
        props.setProperty(SimConfig.DISTRIBUTED_MUTEX, value);

        var e = assertThrows(IllegalArgumentException.class, () -> SimConfig.from(props));
        assertTrue(e.getMessage().contains(SimConfig.DISTRIBUTED_MUTEX), e.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-5", "x"})
    void invalidGrantTimeoutThrowsWithKeyName(String value) {
        var props = new Properties();
        props.setProperty(SimConfig.DISTRIBUTED_GRANT_TIMEOUT_TICKS, value);

        var e = assertThrows(IllegalArgumentException.class, () -> SimConfig.from(props));
        assertTrue(e.getMessage().contains(SimConfig.DISTRIBUTED_GRANT_TIMEOUT_TICKS), e.getMessage());
    }

    @Test
    void logKeysOverrideDefaults() {
        var props = new Properties();
        props.setProperty(SimConfig.LOG_CONSOLE, "false");
        props.setProperty(SimConfig.LOG_LEVEL, "debug");

        SimConfig config = SimConfig.from(props);

        assertFalse(config.logConsole());
        assertEquals(SimLogger.Level.DEBUG, config.logLevel());
    }

    @ParameterizedTest
    @ValueSource(strings = {"TRACE", "verbose", ""})
    void invalidLogLevelThrowsWithKeyName(String value) {
        var props = new Properties();
        props.setProperty(SimConfig.LOG_LEVEL, value);

        var e = assertThrows(IllegalArgumentException.class, () -> SimConfig.from(props));
        assertTrue(e.getMessage().contains(SimConfig.LOG_LEVEL), e.getMessage());
    }

    private static void assertSpecs(SimConfig config) {
        assertEquals(new VehicleSpec(14, 3, 6, 12), config.specFor(VehicleKind.CAR));
        assertEquals(new VehicleSpec(9, 1.5, 4, 20), config.specFor(VehicleKind.BUS));
        assertEquals(new VehicleSpec(20, 4, 8, 14), config.specFor(VehicleKind.AMBULANCE));
    }

    private static void assertStats(SimConfig config) {
        assertEquals(Duration.ofSeconds(60), config.statsThroughputWindow());
        assertEquals(Duration.ofSeconds(1), config.statsSampleInterval());
        assertEquals(300, config.statsSeriesCapacity());
    }
}
