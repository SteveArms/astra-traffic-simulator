package com.astra.traffic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.astra.graph.Intersection;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Con el scheduler real: verde de 300 ms que en SMART puede alargarse 3 × 100 ms. */
@Timeout(10)
class TrafficLightSmartTest {

    private record FixedTimings(Duration greenSeconds, Duration yellowSeconds, Duration allRedSeconds,
            Duration emergencyMaxSeconds) implements LightTimings {
    }

    private record FixedSmart(int smartQueueThreshold, Duration smartExtensionStepSeconds,
            Duration smartMaxExtensionSeconds) implements SmartTimings {
    }

    private static final LightTimings LIGHTS = new FixedTimings(Duration.ofMillis(300), Duration.ofMillis(100),
            Duration.ofMillis(100), Duration.ofSeconds(30));
    private static final SmartTimings SMART = new FixedSmart(3, Duration.ofMillis(100), Duration.ofMillis(300));
    // NS siempre cargado y EW vacío: en SMART el verde de NS se alarga al máximo.
    private static final QueueSensor NS_BUSY = (at, axis) -> axis == Axis.NS ? 10 : 0;
    private static final long EXPECTED_EXTENSION_MILLIS = 300;
    private static final long TOLERANCE_MILLIS = 100;

    private final AtomicReference<LightMode> mode = new AtomicReference<>(LightMode.FIXED);
    private ScheduledExecutorService executor;
    private TrafficLight light;

    @BeforeEach
    void setUp() {
        executor = Executors.newSingleThreadScheduledExecutor();
        light = new TrafficLight(new Intersection("I11", 350, 300), LIGHTS, new GreenState(Axis.NS), () -> 1.0,
                mode::get, NS_BUSY, SMART);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void smartGreenLastsLongerThanFixedWithBusyQueue() throws InterruptedException {
        long fixed = measureNsGreen(LightMode.FIXED);
        long smart = measureNsGreen(LightMode.SMART);

        assertTrue(smart >= fixed + EXPECTED_EXTENSION_MILLIS - TOLERANCE_MILLIS,
                "fixed=" + fixed + " ms, smart=" + smart + " ms");
    }

    @Test
    void smartModeWrapsEveryNewGreen() throws InterruptedException {
        mode.set(LightMode.SMART);
        light.scheduleNext(executor);

        assertInstanceOf(SmartAdaptiveState.class, light.state());
        while (light.colorFor(Axis.EW) != LightColor.GREEN) {
            Thread.sleep(1);
        }
        var ew = assertInstanceOf(SmartAdaptiveState.class, light.state());
        assertTrue(ew.extensionsUsed() == 0);
    }

    @Test
    void switchingBackToFixedLetsCurrentCycleFinish() throws InterruptedException {
        mode.set(LightMode.SMART);
        light.scheduleNext(executor);
        mode.set(LightMode.FIXED);

        while (light.colorFor(Axis.EW) != LightColor.GREEN) {
            Thread.sleep(1);
        }
        assertInstanceOf(GreenState.class, light.state());
    }

    @Test
    void neverTwoAxesGreenInSmartMode() {
        mode.set(LightMode.SMART);
        light.scheduleNext(executor);
        long end = System.nanoTime() + Duration.ofMillis(2_500).toNanos();
        int samples = 0;
        while (System.nanoTime() < end) {
            LightState state = light.state();
            assertFalse(state.colorFor(Axis.NS) == LightColor.GREEN && state.colorFor(Axis.EW) == LightColor.GREEN,
                    state.name());
            samples++;
        }
        assertTrue(light.transitions() > 5, "transitions: " + light.transitions());
        assertTrue(samples > 0);
    }

    private long measureNsGreen(LightMode measuredMode) throws InterruptedException {
        mode.set(measuredMode);
        TestLights.force(light, new GreenState(Axis.NS));
        long start = System.nanoTime();
        light.scheduleNext(executor);
        while (light.colorFor(Axis.NS) == LightColor.GREEN) {
            Thread.sleep(1);
        }
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }
}
