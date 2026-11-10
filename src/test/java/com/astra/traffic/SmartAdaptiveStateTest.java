package com.astra.traffic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.astra.graph.Intersection;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SmartAdaptiveStateTest {

    private record FixedTimings(Duration greenSeconds, Duration yellowSeconds, Duration allRedSeconds,
            Duration emergencyMaxSeconds) implements LightTimings {
    }

    private record FixedSmart(int smartQueueThreshold, Duration smartExtensionStepSeconds,
            Duration smartMaxExtensionSeconds) implements SmartTimings {
    }

    private static final LightTimings LIGHTS = new FixedTimings(Duration.ofSeconds(8), Duration.ofSeconds(2),
            Duration.ofSeconds(1), Duration.ofSeconds(30));
    private static final SmartTimings SMART = new FixedSmart(5, Duration.ofSeconds(2), Duration.ofSeconds(6));
    private static final Intersection AT = new Intersection("I11", 350, 300);

    private final Map<Axis, Integer> queues = new EnumMap<>(Axis.class);
    private final QueueSensor sensor = (at, axis) -> queues.getOrDefault(axis, 0);

    @Test
    void colorsMatchGreenState() {
        var smart = state(Axis.EW, 0);
        var green = new GreenState(Axis.EW);

        for (Axis axis : Axis.values()) {
            assertEquals(green.colorFor(axis), smart.colorFor(axis));
        }
        assertEquals("SMART-EW", smart.name());
        assertEquals("SMART-EW+2", state(Axis.EW, 2).name());
    }

    @Test
    void baseGreenUsesGreenSecondsAndExtensionsUseStep() {
        assertEquals(Duration.ofSeconds(8), state(Axis.NS, 0).duration(LIGHTS));
        assertEquals(Duration.ofSeconds(2), state(Axis.NS, 1).duration(LIGHTS));
    }

    @Test
    void busyActiveAndCalmOppositeExtendsUpToMaxThenYellow() {
        queues.put(Axis.NS, 8);
        queues.put(Axis.EW, 1);

        LightState current = state(Axis.NS, 0);
        for (int expected = 1; expected <= 3; expected++) {
            current = current.next();
            var extended = assertInstanceOf(SmartAdaptiveState.class, current);
            assertEquals(expected, extended.extensionsUsed());
        }

        assertEquals(new YellowState(Axis.NS), current.next());
    }

    @Test
    void busyOppositeDoesNotExtend() {
        queues.put(Axis.NS, 8);
        queues.put(Axis.EW, 5);

        assertEquals(new YellowState(Axis.NS), state(Axis.NS, 0).next());
    }

    @Test
    void shortActiveQueueDoesNotExtend() {
        queues.put(Axis.NS, 4);

        assertEquals(new YellowState(Axis.NS), state(Axis.NS, 0).next());
    }

    @ParameterizedTest
    @CsvSource({"2, 6", "4, 6", "5, 5", "7, 6", "1, 10"})
    void totalExtensionNeverExceedsMax(long stepSeconds, long maxSeconds) {
        var timings = new FixedSmart(1, Duration.ofSeconds(stepSeconds), Duration.ofSeconds(maxSeconds));
        queues.put(Axis.EW, 100);

        LightState current = new SmartAdaptiveState(Axis.EW, AT, sensor, timings, 0);
        Duration extended = Duration.ZERO;
        while (current instanceof SmartAdaptiveState) {
            current = current.next();
            if (current instanceof SmartAdaptiveState) {
                extended = extended.plus(current.duration(LIGHTS));
            }
        }

        assertTrue(extended.compareTo(Duration.ofSeconds(maxSeconds)) <= 0, extended.toString());
        assertEquals(Duration.ofSeconds(stepSeconds * (maxSeconds / stepSeconds)), extended);
        assertEquals(new YellowState(Axis.EW), current);
    }

    @Test
    void negativeExtensionsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> state(Axis.NS, -1));
    }

    private SmartAdaptiveState state(Axis axis, int extensionsUsed) {
        return new SmartAdaptiveState(axis, AT, sensor, SMART, extensionsUsed);
    }
}
