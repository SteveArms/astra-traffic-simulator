package com.astra.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.astra.core.IntersectionZones;
import com.astra.core.SimConfig;
import com.astra.core.SimulationEngine;
import com.astra.core.WorldState;
import com.astra.graph.Intersection;
import com.astra.graph.RoadGraph;
import com.astra.traffic.LightController;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10)
class CommandBusTest {

    private static final int PRODUCERS = 8;
    private static final int COMMANDS_PER_PRODUCER = 10_000;

    private CommandBus bus;
    private SimulationEngine engine;

    @BeforeEach
    void setUp() {
        var graph = new RoadGraph();
        graph.addVertex(new Intersection("A", 0, 0));
        var cfg = SimConfig.from(new Properties());
        var world = new WorldState(graph, cfg, new LightController(graph, cfg), new IntersectionZones(graph, cfg));
        bus = new CommandBus();
        engine = new SimulationEngine(cfg, world, null, bus);
    }

    @AfterEach
    void tearDown() {
        engine.stop();
    }

    @Test
    void executesInFifoOrder() {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            int n = i;
            bus.submit(e -> order.add(n));
        }

        assertEquals(5, bus.drainTo(engine));
        assertEquals(List.of(0, 1, 2, 3, 4), order);
        assertEquals(0, bus.pendingCount());
    }

    @Test
    void failingCommandDoesNotStopTheRest() {
        List<String> ran = new ArrayList<>();
        bus.submit(e -> ran.add("first"));
        bus.submit(e -> {
            throw new IllegalStateException("boom");
        });
        bus.submit(e -> ran.add("third"));

        assertEquals(2, bus.drainTo(engine));
        assertEquals(List.of("first", "third"), ran);
        assertEquals(3, bus.submittedCount());
        assertEquals(2, bus.executedCount());
        assertEquals(1, bus.failedCount());
    }

    @Test
    void submitRejectsNull() {
        assertThrows(NullPointerException.class, () -> bus.submit(null));
        assertEquals(0, bus.submittedCount());
    }

    @Test
    void historyKeepsLastFiftyInOrder() {
        int total = CommandBus.HISTORY_SIZE + 10;
        for (int i = 0; i < total; i++) {
            bus.submit(new Named("CMD-" + i));
        }
        bus.drainTo(engine);

        List<String> history = bus.recentHistory();
        assertEquals(CommandBus.HISTORY_SIZE, history.size());
        assertEquals("#0 CMD-10", history.get(0));
        assertEquals("#0 CMD-" + (total - 1), history.get(history.size() - 1));
        assertThrows(UnsupportedOperationException.class, () -> history.add("x"));
    }

    @Test
    void concurrentProducersAndSingleConsumer() throws Exception {
        var counter = new AtomicLong();
        Command increment = e -> counter.incrementAndGet();
        var start = new CyclicBarrier(PRODUCERS + 1);
        var producersDone = new CountDownLatch(PRODUCERS);
        var producerErrors = Collections.synchronizedList(new ArrayList<Throwable>());

        for (int p = 0; p < PRODUCERS; p++) {
            Thread.ofPlatform().name("producer-" + p).start(() -> {
                try {
                    start.await();
                    for (int i = 0; i < COMMANDS_PER_PRODUCER; i++) {
                        assertTrue(bus.submit(increment));
                    }
                } catch (Throwable t) {
                    producerErrors.add(t);
                } finally {
                    producersDone.countDown();
                }
            });
        }

        var consumerFinished = new AtomicBoolean();
        Thread consumer = Thread.ofPlatform().name("consumer").start(() -> {
            try {
                start.await();
                while (producersDone.getCount() > 0 || bus.pendingCount() > 0) {
                    bus.drainTo(engine);
                }
                consumerFinished.set(true);
            } catch (Exception e) {
                Thread.currentThread().interrupt();
            }
        });

        producersDone.await();
        consumer.join();

        long expected = (long) PRODUCERS * COMMANDS_PER_PRODUCER;
        assertTrue(producerErrors.isEmpty(), () -> producerErrors.toString());
        assertTrue(consumerFinished.get());
        assertEquals(expected, bus.submittedCount());
        assertEquals(expected, bus.executedCount());
        assertEquals(expected, counter.get());
        assertEquals(0, bus.failedCount());
    }

    @Test
    void submittedCommandRunsOnEngineThreadAtStartOfStep() {
        var threadName = new AtomicReference<String>();
        var tickSeen = new AtomicLong(-1);
        assertTrue(engine.commandBus().submit(e -> {
            threadName.set(Thread.currentThread().getName());
            tickSeen.set(e.currentTick());
        }));
        assertFalse(engine.isRunning());

        engine.step();

        assertEquals(SimulationEngine.THREAD_NAME, threadName.get());
        assertEquals(0, tickSeen.get(), "commands run before the tick advances");
        assertEquals(1, engine.latestSnapshot().tick());
        assertEquals(1, bus.executedCount());
    }

    private record Named(String name) implements Command {
        @Override
        public void execute(SimulationEngine engine) {
        }

        @Override
        public String describe() {
            return name;
        }
    }
}
