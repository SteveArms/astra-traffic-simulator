package com.astra.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.astra.traffic.Axis;
import com.astra.traffic.GreenState;
import com.astra.traffic.LightController;
import com.astra.traffic.LightState;
import com.astra.traffic.LightTimings;
import com.astra.traffic.TrafficLight;
import com.astra.traffic.YellowState;
import com.astra.util.MapLoader;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10)
class LightPauseTest {

    private record FixedTimings(Duration greenSeconds, Duration yellowSeconds, Duration allRedSeconds,
            Duration emergencyMaxSeconds) implements LightTimings {
    }

    private static final LightTimings TIMINGS = new FixedTimings(Duration.ofMillis(300), Duration.ofMillis(300),
            Duration.ofMillis(300), Duration.ofSeconds(30));

    private SimulationControl control;
    private LightController lights;
    private TrafficLight light;

    @BeforeEach
    void setUp() {
        control = new SimulationControl();
        lights = new LightController(MapLoader.load("maps/city-3x3.json").graph(), TIMINGS, control::speedFactor);
        control.pauseGate().addListener(PauseListener.of(lights::pause, lights::resume));
        light = lights.lights().iterator().next();
    }

    @AfterEach
    void tearDown() {
        lights.stop();
    }

    @Test
    void pausedLightKeepsItsRemainingTime() throws InterruptedException {
        lights.start();
        Thread.sleep(100);
        control.pauseGate().pause();
        LightState atPause = light.state();
        assertInstanceOf(GreenState.class, atPause);

        Thread.sleep(500);
        assertEquals(atPause, light.state(), "no transition while paused");
        assertEquals(0, light.transitions());

        long resumedAt = System.nanoTime();
        control.pauseGate().resume();
        while (light.state().equals(atPause)) {
            Thread.sleep(1);
        }
        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - resumedAt);

        assertInstanceOf(YellowState.class, light.state());
        assertTrue(elapsed >= 150 && elapsed <= 300, "changed " + elapsed + " ms after resuming");
    }

    @Test
    void preemptWhilePausedDoesNotScheduleAnything() throws InterruptedException {
        lights.start();
        control.pauseGate().pause();
        light.preempt(Axis.EW, "AMB-1");
        light.release("AMB-1");
        LightState afterRelease = light.state();
        Thread.sleep(500);

        assertInstanceOf(YellowState.class, afterRelease);
        assertEquals(afterRelease, light.state());
        control.pauseGate().resume();
        while (light.state().equals(afterRelease)) {
            Thread.sleep(1);
        }
    }

    @Test
    void doubleSpeedHalvesNextPhase() throws InterruptedException {
        control.setSpeed(2.0);
        LightState initial = light.state();
        long startedAt = System.nanoTime();
        lights.start();
        while (light.state().equals(initial)) {
            Thread.sleep(1);
        }
        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

        assertTrue(elapsed >= 100 && elapsed <= 250, "green lasted " + elapsed + " ms at 2x");
    }
}
