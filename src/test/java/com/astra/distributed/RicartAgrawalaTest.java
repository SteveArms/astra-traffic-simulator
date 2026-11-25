package com.astra.distributed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.astra.util.LogSettings;
import com.astra.util.SimLogger;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(20)
class RicartAgrawalaTest {

    private static final int ENTRIES = 200;

    @TempDir
    Path dir;

    private SimulatedNetwork network;

    @BeforeEach
    void setUp() {
        SimLogger.init(new Logs(dir.resolve("astra.log").toString()));
        network = new SimulatedNetwork(new Latency(Duration.ZERO, Duration.ofMillis(3), 7));
        network.start();
    }

    @AfterEach
    void tearDown() {
        network.stop();
        SimLogger.shutdown();
    }

    @Test
    void fourNodesNeverOverlapAllFinishAndPayTwoNMinusOneMessages() throws Exception {
        List<RicartAgrawalaNode> nodes = group("N1", "N2", "N3", "N4");
        var inside = new AtomicInteger();
        var maxInside = new AtomicInteger();
        Map<String, AtomicInteger> entries = new ConcurrentHashMap<>();
        var failures = new CopyOnWriteArrayList<Throwable>();
        List<Thread> workers = new ArrayList<>();
        var start = new CountDownLatch(1);

        for (RicartAgrawalaNode node : nodes) {
            var granted = new Semaphore(0);
            node.setOnEnter(granted::release);
            entries.put(node.processId(), new AtomicInteger());
            Thread worker = new Thread(() -> {
                try {
                    start.await();
                    for (int i = 0; i < ENTRIES; i++) {
                        node.requestCS(node.processId() + "#" + i);
                        granted.acquire();
                        assertTrue(node.isHeld());
                        // Monitor: solo cuenta mientras está dentro; se descuenta antes de salir.
                        maxInside.accumulateAndGet(inside.incrementAndGet(), Math::max);
                        holdFor(ThreadLocalRandom.current().nextInt(1, 4));
                        entries.get(node.processId()).incrementAndGet();
                        inside.decrementAndGet();
                        node.releaseCS();
                    }
                } catch (Throwable t) {
                    failures.add(t);
                }
            }, "ra-" + node.processId());
            workers.add(worker);
            worker.start();
        }
        start.countDown();
        for (Thread worker : workers) {
            worker.join();
        }

        assertTrue(failures.isEmpty(), failures.toString());
        assertEquals(1, maxInside.get(), "more than one node in HELD at once");
        entries.forEach((id, count) -> assertEquals(ENTRIES, count.get(), id + " starved"));
        nodes.forEach(node -> assertEquals(RicartAgrawalaNode.State.RELEASED, node.state()));
        int n = nodes.size();
        assertEquals((long) nodes.size() * ENTRIES * 2 * (n - 1), network.sentCount());
        awaitQuiet();
        assertEquals(network.sentCount(), network.deliveredCount());
    }

    @Test
    void singleEntryCostsTwoNMinusOneMessages() throws Exception {
        List<RicartAgrawalaNode> nodes = group("N1", "N2", "N3", "N4", "N5");
        var granted = new CountDownLatch(1);
        nodes.get(0).setOnEnter(granted::countDown);

        nodes.get(0).requestCS("N1#0");
        assertTrue(granted.await(5, TimeUnit.SECONDS));
        nodes.get(0).releaseCS();
        awaitQuiet();

        assertEquals(2 * (nodes.size() - 1), network.sentCount());
    }

    @Test
    void concurrentRequestsEnterByTimestampThenProcessId() throws Exception {
        List<RicartAgrawalaNode> nodes = group("N1", "N2", "N3");
        RicartAgrawalaNode n1 = nodes.get(0);
        RicartAgrawalaNode n2 = nodes.get(1);
        RicartAgrawalaNode n3 = nodes.get(2);
        // N1 pide con timestamp 3; N2 y N3 con 1 y empatan: desempata el processId.
        n1.clock().tick("LOCAL");
        n1.clock().tick("LOCAL");
        List<String> order = new CopyOnWriteArrayList<>();
        var done = new CountDownLatch(nodes.size());
        for (RicartAgrawalaNode node : nodes) {
            node.setOnEnter(() -> {
                order.add(node.processId());
                node.releaseCS();
                done.countDown();
            });
        }

        // En pausa los tres pedidos salen antes de que llegue ningún mensaje: son concurrentes.
        network.onPause();
        n3.requestCS("N3#0");
        n1.requestCS("N1#0");
        n2.requestCS("N2#0");
        network.onResume();

        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertEquals(List.of("N2", "N3", "N1"), order);
    }

    @Test
    void loneNodeEntersWithoutMessages() {
        var node = RicartAgrawalaNode.join("SOLO", List.of(), network);
        var entered = new AtomicInteger();
        node.setOnEnter(entered::incrementAndGet);

        node.requestCS("SOLO#0");

        assertTrue(node.isHeld());
        assertEquals(1, entered.get());
        node.releaseCS();
        assertEquals(0, network.sentCount());
    }

    @Test
    void rejectsSecondRequestAndReleaseWithoutHolding() {
        List<RicartAgrawalaNode> nodes = group("N1", "N2");
        RicartAgrawalaNode n1 = nodes.get(0);

        assertThrows(IllegalStateException.class, n1::releaseCS);
        n1.requestCS("N1#0");
        assertThrows(IllegalStateException.class, () -> n1.requestCS("N1#1"));
        assertFalse(nodes.get(1).isHeld());
    }

    private List<RicartAgrawalaNode> group(String... ids) {
        List<RicartAgrawalaNode> nodes = new ArrayList<>();
        for (String id : ids) {
            List<String> peers = new ArrayList<>(List.of(ids));
            peers.remove(id);
            nodes.add(RicartAgrawalaNode.join(id, peers, network));
        }
        return nodes;
    }

    // Espera activa y no sleep: en Windows la granularidad de sleep crece si el proceso pasa a segundo plano, y
    // 800 esperas de 1-3 ms llegaban a durar ~8 ms cada una.
    private static void holdFor(long millis) {
        long until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
        while (System.nanoTime() < until) {
            Thread.onSpinWait();
        }
    }

    private void awaitQuiet() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (network.inFlightCount() > 0 && System.nanoTime() < deadline) {
            Thread.sleep(5);
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
