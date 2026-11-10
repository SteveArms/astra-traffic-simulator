package com.astra.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.astra.graph.RoadGraph;
import com.astra.model.VehicleKind;
import com.astra.traffic.LightController;
import com.astra.util.MapLoader;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class SimStatsTest {

    private static final double DELTA = 1e-9;
    private static final double DT = 0.05;

    private final SimStats stats = new SimStats(Duration.ofSeconds(60), Duration.ofSeconds(1), 300);

    @Test
    void averagesAreExactForKnownArrivals() {
        stats.advance(DT);
        stats.recordArrival(VehicleKind.CAR, 20, 4);
        stats.recordArrival(VehicleKind.CAR, 30, 6);
        stats.recordArrival(VehicleKind.BUS, 40, 11);
        stats.sample(5, 0);

        StatsView view = stats.toView();
        assertEquals(3, view.totalArrived());
        assertEquals(30, view.avgTripSeconds(), DELTA);
        assertEquals(7, view.avgWaitSeconds(), DELTA);
        assertEquals(Map.of(VehicleKind.CAR, 5.0, VehicleKind.BUS, 11.0), view.avgWaitByKind());
    }

    @Test
    void emptyStatsAreZero() {
        stats.sample(0, 0);

        StatsView view = stats.toView();
        assertEquals(0, view.avgTripSeconds());
        assertEquals(0, view.avgWaitSeconds());
        assertEquals(Map.of(), view.avgWaitByKind());
    }

    @Test
    void throughputWindowDropsArrivalsOlderThanSixtySeconds() {
        stats.advance(1);
        stats.recordArrival(VehicleKind.CAR, 10, 0);
        stats.recordArrival(VehicleKind.CAR, 10, 0);
        stats.advance(30);
        stats.recordArrival(VehicleKind.BUS, 10, 0);
        stats.sample(0, 0);
        assertEquals(3, stats.toView().throughputPerMin(), DELTA);

        stats.advance(30);
        stats.sample(0, 0);
        assertEquals(3, stats.toView().throughputPerMin(), DELTA, "exactly 60 s ago is still inside");

        stats.advance(1);
        stats.sample(0, 0);
        assertEquals(1, stats.toView().throughputPerMin(), DELTA);
        assertEquals(3, stats.toView().totalArrived());
    }

    @Test
    void throughputIsScaledToPerMinute() {
        var shortWindow = new SimStats(Duration.ofSeconds(30), Duration.ofSeconds(1), 10);
        shortWindow.advance(1);
        shortWindow.recordArrival(VehicleKind.CAR, 1, 0);
        shortWindow.sample(0, 0);

        assertEquals(2, shortWindow.toView().throughputPerMin(), DELTA);
    }

    @Test
    void seriesNeverExceedsCapacityAndKeepsNewest() {
        int ticks = 400 * 20;
        for (int i = 0; i < ticks; i++) {
            stats.advance(DT);
            stats.sample(i, 0);
        }

        List<StatsPoint> series = stats.toView().series();
        assertEquals(300, series.size());
        assertEquals(400, series.get(series.size() - 1).simTimeSeconds(), 1e-6);
        assertEquals(101, series.get(0).simTimeSeconds(), 1e-6);
        assertThrows(UnsupportedOperationException.class, () -> series.add(series.get(0)));
    }

    @Test
    void seriesAddsOnePointPerSimulatedSecond() {
        for (int i = 0; i < 20 * 5; i++) {
            stats.advance(DT);
            stats.sample(7, 0);
        }

        List<StatsPoint> series = stats.toView().series();
        assertEquals(5, series.size());
        assertEquals(7, series.get(0).active());
    }

    @Test
    void queueTracksCurrentAndMax() {
        stats.sample(0, 4);
        stats.sample(0, 9);
        stats.sample(0, 2);

        assertEquals(2, stats.toView().currentQueue());
        assertEquals(9, stats.toView().maxQueue());
    }

    @Test
    @Timeout(60)
    void headlessRunMatchesSpawnerCounts() {
        var cfg = SimConfig.from(new Properties());
        RoadGraph graph = MapLoader.load(cfg.mapFile()).graph();
        var world = new WorldState(graph, cfg, new LightController(graph, cfg), new IntersectionZones(graph, cfg));
        var spawner = new VehicleSpawner(world, cfg);
        var engine = new SimulationEngine(cfg, world, spawner);
        try {
            for (int i = 0; i < 3_000; i++) {
                engine.step();
            }
        } finally {
            engine.stop();
        }

        StatsView view = engine.latestSnapshot().stats();
        assertTrue(view.totalArrived() > 0);
        assertEquals(spawner.despawned(), view.totalArrived());
        assertTrue(view.avgWaitSeconds() >= 0 && view.avgWaitSeconds() <= view.avgTripSeconds(), view.toString());
        assertEquals(150, view.series().size());
    }
}
