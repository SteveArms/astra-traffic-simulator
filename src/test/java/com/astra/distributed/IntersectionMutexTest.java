package com.astra.distributed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.astra.graph.Graph;
import com.astra.graph.Intersection;
import com.astra.graph.Road;
import com.astra.util.LogSettings;
import com.astra.util.SimLogger;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(20)
class IntersectionMutexTest {

    private static final Intersection CENTER = new Intersection("C", 0, 0);
    private static final Intersection WEST = new Intersection("W", -100, 0);
    private static final Intersection NORTH = new Intersection("N", 0, -100);
    private static final Road FROM_WEST = new Road("w-c", WEST, CENTER, 100, 10, 1);
    private static final Road FROM_NORTH = new Road("n-c", NORTH, CENTER, 100, 10, 1);
    private static final Road TO_WEST = new Road("c-w", CENTER, WEST, 100, 10, 1);

    @TempDir
    Path dir;

    private SimulatedNetwork network;
    private IntersectionMutex mutex;

    @BeforeEach
    void setUp() {
        SimLogger.init(new Logs(dir.resolve("astra.log").toString()));
        network = new SimulatedNetwork(new Latency(Duration.ofMillis(1), Duration.ofMillis(10), 7));
        network.start();
        var graph = new Graph<Intersection, Road>();
        List.of(CENTER, WEST, NORTH).forEach(graph::addVertex);
        List.of(FROM_WEST, FROM_NORTH, TO_WEST).forEach(graph::addEdge);
        mutex = IntersectionMutex.of(graph, CENTER, network);
    }

    @AfterEach
    void tearDown() {
        network.stop();
        SimLogger.shutdown();
    }

    @Test
    void vehiclesOfSameAccessEnterInFifoOrder() {
        mutex.enqueue(FROM_WEST, "CAR-1");
        mutex.enqueue(FROM_WEST, "CAR-2");
        mutex.enqueue(FROM_WEST, "CAR-3");
        List<String> entered = new ArrayList<>();

        for (String expected : List.of("CAR-1", "CAR-2", "CAR-3")) {
            await(() -> mutex.isGranted(expected));
            assertEquals(expected, mutex.holder());
            entered.add(expected);
            mutex.release(expected);
        }

        assertEquals(List.of("CAR-1", "CAR-2", "CAR-3"), entered);
        await(() -> network.inFlightCount() == 0);
        assertNull(mutex.holder());
        assertEquals(List.of(), mutex.waiting());
    }

    @Test
    void onlyHeadOfQueueIsGrantedAndOthersWait() {
        mutex.enqueue(FROM_WEST, "CAR-1");
        mutex.enqueue(FROM_WEST, "CAR-2");
        await(() -> mutex.isGranted("CAR-1"));

        assertFalse(mutex.isGranted("CAR-2"));
        assertEquals(List.of("CAR-2"), mutex.waiting());
    }

    @Test
    void cancellingHolderReleasesSectionToOtherAccess() {
        mutex.enqueue(FROM_WEST, "CAR-1");
        mutex.enqueue(FROM_NORTH, "BUS-1");
        await(() -> mutex.holder() != null);
        String holder = mutex.holder();
        String other = holder.equals("CAR-1") ? "BUS-1" : "CAR-1";
        assertFalse(mutex.isGranted(other));

        assertTrue(mutex.cancel(holder));

        await(() -> mutex.isGranted(other));
        assertEquals(other, mutex.holder());
        assertFalse(mutex.isGranted(holder));
    }

    @Test
    void cancellingWaitingVehicleRemovesItFromQueue() {
        mutex.enqueue(FROM_WEST, "CAR-1");
        mutex.enqueue(FROM_WEST, "CAR-2");
        mutex.enqueue(FROM_WEST, "CAR-3");
        await(() -> mutex.isGranted("CAR-1"));

        assertTrue(mutex.cancel("CAR-2"));
        assertFalse(mutex.cancel("CAR-2"));
        mutex.release("CAR-1");

        await(() -> mutex.isGranted("CAR-3"));
        assertEquals(List.of(), mutex.waiting());
    }

    @Test
    void cancelledHeadBeforeGrantDoesNotKeepSection() {
        network.onPause();
        mutex.enqueue(FROM_WEST, "CAR-1");
        assertTrue(mutex.cancel("CAR-1"));
        mutex.enqueue(FROM_NORTH, "BUS-1");
        network.onResume();

        await(() -> mutex.isGranted("BUS-1"));
        mutex.release("BUS-1");
        await(() -> network.inFlightCount() == 0);
        assertNull(mutex.holder());
    }

    @Test
    void rejectsInvalidUse() {
        assertThrows(IllegalArgumentException.class, () -> mutex.enqueue(TO_WEST, "CAR-1"));
        mutex.enqueue(FROM_WEST, "CAR-1");
        assertThrows(IllegalStateException.class, () -> mutex.enqueue(FROM_NORTH, "CAR-1"));
        assertThrows(IllegalStateException.class, () -> mutex.release("CAR-9"));
    }

    @Test
    void singleAccessIsGrantedWithoutMessages() {
        var lone = new Intersection("L", 200, 0);
        var mutexL = new IntersectionMutex(lone, List.of(new Road("c-l", CENTER, lone, 200, 10, 1)), network);

        mutexL.enqueue(new Road("c-l", CENTER, lone, 200, 10, 1), "CAR-1");

        assertTrue(mutexL.isGranted("CAR-1"));
        assertEquals(0, network.sentCount());
    }

    private static void await(BooleanSupplier condition) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("condition not met in time");
            }
            Thread.onSpinWait();
        }
    }

    private record Latency(Duration latencyMin, Duration latencyMax, long networkSeed) implements NetworkSettings {
    }

    private record Logs(String logFile) implements LogSettings {

        @Override
        public boolean logConsole() {
            return false;
        }

        @Override
        public SimLogger.Level logLevel() {
            return SimLogger.Level.INFO;
        }
    }
}
