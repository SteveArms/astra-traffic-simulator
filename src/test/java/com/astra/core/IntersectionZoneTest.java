package com.astra.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.astra.graph.Intersection;
import com.astra.util.MapLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10)
class IntersectionZoneTest {

    private static final int CAPACITY = 2;
    private static final Intersection CENTER = new Intersection("I11", 350, 300);

    private final IntersectionZone zone = new IntersectionZone(CENTER, CAPACITY);

    @Test
    void thirdVehicleWaitsUntilOneLeaves() {
        assertTrue(zone.tryEnter("CAR-1"));
        assertTrue(zone.tryEnter("CAR-2"));
        assertTrue(zone.isFull());

        assertFalse(zone.tryEnter("CAR-3"));

        zone.leave("CAR-1");
        assertTrue(zone.tryEnter("CAR-3"));
        assertEquals(2, zone.occupancy());
    }

    @Test
    void repeatedEnterDoesNotTakeAnotherPermit() {
        assertTrue(zone.tryEnter("CAR-1"));
        assertTrue(zone.tryEnter("CAR-1"));

        assertEquals(1, zone.occupancy());
        assertFalse(zone.isFull());
        assertTrue(zone.tryEnter("CAR-2"));
    }

    @Test
    void doubleLeaveNeverExceedsCapacity() {
        zone.tryEnter("CAR-1");
        zone.leave("CAR-1");
        zone.leave("CAR-1");
        zone.leave("GHOST");

        assertTrue(zone.tryEnter("CAR-1"));
        assertTrue(zone.tryEnter("CAR-2"));
        assertFalse(zone.tryEnter("CAR-3"));
    }

    @Test
    void occupancyNeverExceedsCapacityUnderContention() throws Exception {
        int threads = 20;
        int iterations = 1_000;
        var maxSeen = new AtomicInteger();
        var entries = new AtomicInteger();
        var start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                String vehicleId = "CAR-" + t;
                futures.add(pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < iterations; i++) {
                        if (zone.tryEnter(vehicleId)) {
                            entries.incrementAndGet();
                            maxSeen.accumulateAndGet(zone.occupancy(), Math::max);
                            zone.leave(vehicleId);
                        }
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            pool.shutdownNow();
        }

        assertTrue(maxSeen.get() <= CAPACITY, "max " + maxSeen.get());
        assertTrue(entries.get() > 0);
        assertEquals(0, zone.occupancy());
        assertFalse(zone.isFull());
    }

    @Test
    void zonesCoverEveryIntersectionAndReleaseAll() {
        var graph = MapLoader.load("maps/city-3x3.json").graph();
        var zones = new IntersectionZones(graph, SimConfig.load());
        Intersection i00 = graph.findIntersection("I00").orElseThrow();
        Intersection i11 = graph.findIntersection("I11").orElseThrow();

        assertEquals(9, zones.zones().size());
        assertSame(zones.zoneOf(i00), zones.zoneOf(i00));
        zones.zoneOf(i00).tryEnter("CAR-1");
        zones.zoneOf(i11).tryEnter("CAR-1");

        zones.releaseAll("CAR-1");

        assertEquals(0, zones.zoneOf(i00).occupancy());
        assertEquals(0, zones.zoneOf(i11).occupancy());
        assertThrows(IllegalArgumentException.class, () -> zones.zoneOf(new Intersection("X", 0, 0)));
    }
}
