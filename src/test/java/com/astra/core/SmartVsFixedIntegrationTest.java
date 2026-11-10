package com.astra.core;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.astra.graph.RoadGraph;
import com.astra.model.VehicleView;
import com.astra.traffic.LightController;
import com.astra.traffic.LightMode;
import com.astra.traffic.ManualScheduler;
import com.astra.util.MapLoader;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Determinista: motor secuencial con step() y semáforos en tiempo simulado con {@link ManualScheduler}. */
@Timeout(120)
class SmartVsFixedIntegrationTest {

    private static final int TICKS = 5_000;
    private static final double TOLERANCE = 1.10;
    private static final double STOPPED_SPEED = 0.5;
    private static final long CONFIG_SEED = 42;
    private static final long[] SEEDS = {42, 7, 2026, 99, 1234};

    @Test
    void smartWaitsNoMoreThanFixedPlusTenPercent() {
        double fixed = averageWaitSeconds(LightMode.FIXED, CONFIG_SEED);
        double smart = averageWaitSeconds(LightMode.SMART, CONFIG_SEED);

        assertTrue(smart <= fixed * TOLERANCE, "FIXED=" + fixed + " s, SMART=" + smart + " s");
    }

    @Test
    void smartReducesAverageWaitAcrossSeeds() {
        double fixed = LongStream.of(SEEDS).mapToDouble(s -> averageWaitSeconds(LightMode.FIXED, s)).average()
                .orElseThrow();
        double smart = LongStream.of(SEEDS).mapToDouble(s -> averageWaitSeconds(LightMode.SMART, s)).average()
                .orElseThrow();

        assertTrue(smart < fixed, "FIXED=" + fixed + " s, SMART=" + smart + " s");
    }

    private static double averageWaitSeconds(LightMode mode, long seed) {
        var props = new Properties();
        props.setProperty(SimConfig.ENGINE_PARALLEL, "false");
        props.setProperty(SimConfig.SPAWN_SEED, Long.toString(seed));
        var cfg = SimConfig.from(props);
        RoadGraph graph = MapLoader.load(cfg.mapFile()).graph();
        var lights = new LightController(graph, cfg, cfg, () -> 1.0);
        lights.setMode(mode);
        var world = new WorldState(graph, cfg, lights, new IntersectionZones(graph, cfg));
        var engine = new SimulationEngine(cfg, world, new VehicleSpawner(world, cfg));
        var clock = new ManualScheduler();
        lights.lights().forEach(light -> light.scheduleNext(clock));
        Duration dt = cfg.tickMillis();
        Map<String, Integer> waitTicks = new HashMap<>();
        try {
            for (int i = 0; i < TICKS; i++) {
                engine.step();
                clock.advance(dt);
                for (VehicleView v : engine.latestSnapshot().vehicles()) {
                    waitTicks.merge(v.id(), v.speed() < STOPPED_SPEED ? 1 : 0, Integer::sum);
                }
            }
        } finally {
            engine.stop();
        }
        double avgTicks = waitTicks.values().stream().mapToInt(Integer::intValue).average().orElse(0);
        return avgTicks * dt.toMillis() / 1_000.0;
    }
}
