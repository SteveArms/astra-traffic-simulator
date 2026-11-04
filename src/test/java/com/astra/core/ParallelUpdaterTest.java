package com.astra.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10)
class ParallelUpdaterTest {

    private static final int WORKERS = 4;
    private static final int ITEMS = 1_000;
    private static final int TICKS = 20;
    private static final List<Integer> ELEMENTS = IntStream.range(0, ITEMS).boxed().toList();

    private final ParallelUpdater updater = new ParallelUpdater(WORKERS, Duration.ofSeconds(2));

    @AfterEach
    void tearDown() {
        updater.shutdown();
    }

    @Test
    void everyIndexIsPlannedAndMovedOncePerTick() {
        var planned = new AtomicIntegerArray(ITEMS);
        var moved = new AtomicIntegerArray(ITEMS);

        for (int tick = 1; tick <= TICKS; tick++) {
            updater.run(ELEMENTS, planned::incrementAndGet, moved::incrementAndGet);
            for (int i = 0; i < ITEMS; i++) {
                assertEquals(tick, planned.get(i), "plan of " + i);
                assertEquals(tick, moved.get(i), "move of " + i);
            }
        }
    }

    @Test
    void allPlansFinishBeforeAnyMove() {
        var clock = new AtomicLong();
        var planStamps = new AtomicLongArray(ITEMS);
        var moveStamps = new AtomicLongArray(ITEMS);

        for (int tick = 0; tick < TICKS; tick++) {
            updater.run(ELEMENTS, i -> planStamps.set(i, clock.incrementAndGet()),
                    i -> moveStamps.set(i, clock.incrementAndGet()));

            long lastPlan = IntStream.range(0, ITEMS).mapToLong(planStamps::get).max().orElseThrow();
            long firstMove = IntStream.range(0, ITEMS).mapToLong(moveStamps::get).min().orElseThrow();
            assertTrue(lastPlan < firstMove, "plan at " + lastPlan + " after move at " + firstMove);
        }
    }

    @Test
    void runsOnNamedWorkerThreads() {
        Set<String> names = ConcurrentHashMap.newKeySet();

        updater.run(ELEMENTS, i -> names.add(Thread.currentThread().getName()), i -> { });

        assertEquals(WORKERS, names.size(), names.toString());
        assertTrue(names.stream().allMatch(n -> n.startsWith(ParallelUpdater.THREAD_PREFIX)), names.toString());
    }

    @Test
    void planFailureReachesEngineWithoutHangingAndSkipsMove() {
        var boom = new IllegalArgumentException("boom");
        var moved = new AtomicIntegerArray(ITEMS);

        var thrown = assertThrows(IllegalArgumentException.class, () -> updater.run(ELEMENTS, i -> {
            if (i == ITEMS / 2) {
                throw boom;
            }
        }, moved::incrementAndGet));

        assertSame(boom, thrown);
        assertTrue(IntStream.range(0, ITEMS).allMatch(i -> moved.get(i) == 0), "MOVE ran after a PLAN failure");
        // El pool sigue sano.
        updater.run(ELEMENTS, i -> { }, moved::incrementAndGet);
        assertEquals(1, moved.get(0));
    }

    @Test
    void moveFailureReachesEngine() {
        assertThrows(IllegalStateException.class, () -> updater.run(ELEMENTS, i -> { }, i -> {
            if (i == 0) {
                throw new IllegalStateException("move failed");
            }
        }));
    }

    @Test
    void phaseTimeoutNamesThePhase() {
        var slow = new ParallelUpdater(WORKERS, Duration.ofMillis(50));
        try {
            var e = assertThrows(IllegalStateException.class, () -> slow.run(ELEMENTS, i -> {
                if (i == 0) {
                    sleep(500);
                }
            }, i -> { }));
            assertTrue(e.getMessage().contains("PLAN"), e.getMessage());
        } finally {
            slow.shutdown();
        }
    }

    @Test
    void emptyListAndFewerItemsThanWorkersWork() {
        var moved = new AtomicIntegerArray(2);

        updater.run(Collections.emptyList(), i -> { }, i -> { });
        updater.run(List.of("a", "b"), i -> { }, moved::incrementAndGet);

        assertEquals(1, moved.get(0));
        assertEquals(1, moved.get(1));
    }

    @Test
    void shutdownStopsWorkerThreads() throws InterruptedException {
        Set<Thread> threads = ConcurrentHashMap.newKeySet();
        updater.run(ELEMENTS, i -> threads.add(Thread.currentThread()), i -> { });
        assertEquals(WORKERS, threads.size());

        updater.shutdown();

        for (Thread thread : threads) {
            thread.join(1_000);
            assertFalse(thread.isAlive(), thread.getName());
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
