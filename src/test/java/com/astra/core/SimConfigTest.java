package com.astra.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SimConfigTest {

    @Test
    void loadReadsRealConfigFile() {
        SimConfig config = SimConfig.load();

        assertEquals(Duration.ofMillis(50), config.tickMillis());
        assertEquals(Duration.ofSeconds(8), config.greenSeconds());
        assertEquals(Duration.ofSeconds(2), config.yellowSeconds());
        assertEquals(Duration.ofSeconds(1), config.allRedSeconds());
        assertEquals(5, config.smartQueueThreshold());
        assertEquals(Duration.ofSeconds(6), config.smartMaxExtensionSeconds());
        assertEquals(Duration.ofMillis(1500), config.spawnIntervalMillis());
        assertEquals(150, config.vehicleMaxCount());
        assertEquals(14, config.carMaxSpeed());
        assertEquals(3, config.carAcceleration());
        assertEquals(6, config.carDeceleration());
        assertEquals(10, config.minGap());
        assertEquals(14, config.stopLineOffset());
        assertEquals(2, config.intersectionCapacity());
        assertEquals(16, config.boxLength());
        assertEquals(2, config.approachMargin());
        assertEquals(2.0, config.congestionFactor());
        assertEquals(0.6, config.randomJitter());
        assertEquals(4, config.demoCarCount());
        assertEquals(42, config.demoSeed());
        assertEquals("maps/city-3x3.json", config.mapFile());
        assertEquals("logs/astra.log", config.logFile());
    }

    @Test
    void missingKeysUseDefaults() {
        SimConfig config = SimConfig.from(new Properties());

        assertEquals(Duration.ofMillis(50), config.tickMillis());
        assertEquals(Duration.ofSeconds(8), config.greenSeconds());
        assertEquals(Duration.ofSeconds(2), config.yellowSeconds());
        assertEquals(Duration.ofSeconds(1), config.allRedSeconds());
        assertEquals(5, config.smartQueueThreshold());
        assertEquals(Duration.ofSeconds(6), config.smartMaxExtensionSeconds());
        assertEquals(Duration.ofMillis(1500), config.spawnIntervalMillis());
        assertEquals(150, config.vehicleMaxCount());
        assertEquals(14, config.carMaxSpeed());
        assertEquals(3, config.carAcceleration());
        assertEquals(6, config.carDeceleration());
        assertEquals(10, config.minGap());
        assertEquals(14, config.stopLineOffset());
        assertEquals(2, config.intersectionCapacity());
        assertEquals(16, config.boxLength());
        assertEquals(2, config.approachMargin());
        assertEquals(2.0, config.congestionFactor());
        assertEquals(0.6, config.randomJitter());
        assertEquals(4, config.demoCarCount());
        assertEquals(42, config.demoSeed());
        assertEquals("maps/city-3x3.json", config.mapFile());
        assertEquals("logs/astra.log", config.logFile());
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
        SimConfig.APPROACH_MARGIN, SimConfig.ROUTING_CONGESTION_FACTOR, SimConfig.ROUTING_RANDOM_JITTER
    })
    void newKeysRejectNonPositiveValues(String key) {
        var props = new Properties();
        props.setProperty(key, "0");

        var e = assertThrows(IllegalArgumentException.class, () -> SimConfig.from(props));
        assertTrue(e.getMessage().contains(key), e.getMessage());
    }
}
