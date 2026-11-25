package com.astra.distributed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.astra.util.LogSettings;
import com.astra.util.SimLogger;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(10)
class SimulatedNetworkTest {

    private static final long MIN_MILLIS = 5;
    private static final long MAX_MILLIS = 40;
    private static final long TOLERANCE_MILLIS = 30;

    @TempDir
    Path dir;

    private SimulatedNetwork network;

    @BeforeEach
    void setUp() {
        SimLogger.init(new Logs(dir.resolve("astra.log").toString()));
        network = new SimulatedNetwork(new Latency(Duration.ofMillis(MIN_MILLIS), Duration.ofMillis(MAX_MILLIS), 7));
        network.register("A", m -> { });
        network.start();
    }

    @AfterEach
    void tearDown() {
        network.stop();
        SimLogger.shutdown();
    }

    @Test
    void everyMessageIsDeliveredExactlyOnceOnTheNetThread() throws Exception {
        int total = 200;
        var received = new ConcurrentHashMap<String, AtomicInteger>();
        var threads = new CopyOnWriteArrayList<String>();
        var done = new CountDownLatch(total);
        network.register("B", m -> {
            received.computeIfAbsent(m.requestId(), id -> new AtomicInteger()).incrementAndGet();
            threads.add(Thread.currentThread().getName());
            done.countDown();
        });

        for (int i = 0; i < total; i++) {
            assertTrue(network.send(request("A", "B", i)));
        }
        assertTrue(done.await(5, TimeUnit.SECONDS));
        // Margen para detectar una entrega duplicada tardía.
        Thread.sleep(MAX_MILLIS + TOLERANCE_MILLIS);

        assertEquals(total, received.size());
        received.forEach((id, count) -> assertEquals(1, count.get(), id));
        assertEquals(total, network.sentCount());
        assertEquals(network.sentCount(), network.deliveredCount());
        assertEquals(0, network.inFlightCount());
        assertTrue(threads.stream().allMatch(SimulatedNetwork.THREAD_NAME::equals), threads.toString());
    }

    @Test
    void latencyStaysWithinConfiguredRange() throws Exception {
        int total = 100;
        var sentAt = new ConcurrentHashMap<String, Long>();
        var latencies = new CopyOnWriteArrayList<Long>();
        var done = new CountDownLatch(total);
        network.register("B", m -> {
            latencies.add(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - sentAt.get(m.requestId())));
            done.countDown();
        });

        for (int i = 0; i < total; i++) {
            Message m = request("A", "B", i);
            sentAt.put(m.requestId(), System.nanoTime());
            network.send(m);
        }
        assertTrue(done.await(5, TimeUnit.SECONDS));

        for (long latency : latencies) {
            assertTrue(latency >= MIN_MILLIS && latency <= MAX_MILLIS + TOLERANCE_MILLIS, "latency " + latency);
        }
    }

    /**
     * Propiedad documentada, no un defecto: la red no es FIFO. Ricart-Agrawala no la necesita, porque cada petición
     * lleva su timestamp de Lamport y el orden lo decide {@link Request}, no el orden de llegada.
     */
    @Test
    void messagesFromSameSenderMayArriveOutOfOrder() throws Exception {
        int total = 50;
        List<Integer> arrival = new CopyOnWriteArrayList<>();
        var done = new CountDownLatch(total);
        network.register("B", m -> {
            arrival.add(m.timestamp());
            done.countDown();
        });

        for (int i = 0; i < total; i++) {
            network.send(request("A", "B", i));
        }
        assertTrue(done.await(5, TimeUnit.SECONDS));

        boolean reordered = false;
        for (int i = 1; i < arrival.size(); i++) {
            reordered |= arrival.get(i) < arrival.get(i - 1);
        }
        assertTrue(reordered, "expected at least one inversion: " + arrival);
    }

    @Test
    void pauseHoldsMessagesAndResumeDeliversThemAll() throws Exception {
        int total = 20;
        var done = new CountDownLatch(total);
        network.register("B", m -> done.countDown());

        network.onPause();
        for (int i = 0; i < total; i++) {
            network.send(request("A", "B", i));
        }
        Thread.sleep(MAX_MILLIS + TOLERANCE_MILLIS * 3);

        assertEquals(0, network.deliveredCount());
        assertEquals(total, network.heldCount());
        assertEquals(total, network.inFlightCount());

        network.onResume();
        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertEquals(total, network.deliveredCount());
        assertEquals(0, network.heldCount());
        assertEquals(0, network.inFlightCount());
    }

    @Test
    void unknownDestinationIsDroppedWithWarning() {
        assertFalse(network.send(request("A", "NOBODY", 1)));

        assertEquals(0, network.sentCount());
        assertEquals(1, SimLogger.lineCount(SimLogger.Category.NET));
    }

    @Test
    void stopIsIdempotentAndRejectsLaterSends() {
        network.register("B", m -> { });
        network.stop();
        network.stop();

        assertFalse(network.send(request("A", "B", 1)));
        assertEquals(0, network.sentCount());
    }

    private static Message request(String from, String to, int ts) {
        return new Message(MessageType.REQUEST, from, to, ts, from + "#" + ts);
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
