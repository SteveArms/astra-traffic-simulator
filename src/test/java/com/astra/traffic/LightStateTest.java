package com.astra.traffic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LightStateTest {

    private record FixedTimings(Duration greenSeconds, Duration yellowSeconds, Duration allRedSeconds)
            implements LightTimings {
    }

    private static final LightTimings TIMINGS =
            new FixedTimings(Duration.ofSeconds(7), Duration.ofSeconds(3), Duration.ofSeconds(1));

    @Test
    void twelveTransitionsFollowTheExactCycle() {
        List<LightState> expectedCycle = List.of(
                new GreenState(Axis.NS), new YellowState(Axis.NS), new RedState(Axis.EW),
                new GreenState(Axis.EW), new YellowState(Axis.EW), new RedState(Axis.NS));

        List<LightState> visited = new ArrayList<>();
        LightState state = new GreenState(Axis.NS);
        for (int i = 0; i <= 12; i++) {
            visited.add(state);
            state = state.next();
        }

        for (int i = 0; i < visited.size(); i++) {
            assertEquals(expectedCycle.get(i % expectedCycle.size()), visited.get(i), "transition " + i);
        }
    }

    @Test
    void neverTwoAxesOutOfRedAtTheSameTime() {
        LightState state = new GreenState(Axis.NS);
        for (int i = 0; i <= 12; i++) {
            boolean nsActive = state.colorFor(Axis.NS) != LightColor.RED;
            boolean ewActive = state.colorFor(Axis.EW) != LightColor.RED;
            assertFalse(nsActive && ewActive, state.name());
            state = state.next();
        }
    }

    @Test
    void durationsComeFromTimings() {
        assertEquals(TIMINGS.greenSeconds(), new GreenState(Axis.NS).duration(TIMINGS));
        assertEquals(TIMINGS.yellowSeconds(), new YellowState(Axis.EW).duration(TIMINGS));
        assertEquals(TIMINGS.allRedSeconds(), new RedState(Axis.NS).duration(TIMINGS));
    }

    @Test
    void onlyGreenAxisCanPass() {
        assertTrue(new GreenState(Axis.NS).canPass(Axis.NS));
        assertFalse(new GreenState(Axis.NS).canPass(Axis.EW));
        assertFalse(new YellowState(Axis.NS).canPass(Axis.NS));
        assertFalse(new RedState(Axis.NS).canPass(Axis.NS));
        assertFalse(new RedState(Axis.NS).canPass(Axis.EW));
    }

    @Test
    void namesIdentifyStateAndAxis() {
        assertEquals("GREEN(NS)", new GreenState(Axis.NS).name());
        assertEquals("YELLOW(EW)", new YellowState(Axis.EW).name());
        assertEquals("RED(next NS)", new RedState(Axis.NS).name());
    }
}
