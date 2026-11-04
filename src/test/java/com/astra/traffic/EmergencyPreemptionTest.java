package com.astra.traffic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.astra.graph.Intersection;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** TrafficLight real con su scheduler y tiempos cortos. */
@Timeout(10)
class EmergencyPreemptionTest {

    private record FixedTimings(Duration greenSeconds, Duration yellowSeconds, Duration allRedSeconds,
            Duration emergencyMaxSeconds) implements LightTimings {
    }

    private static final long MAX_PREEMPT_MILLIS = 50;
    private static final Duration SAMPLING = Duration.ofSeconds(1);
    private static final int THREADS = 10;

    private ScheduledExecutorService scheduler;

    @BeforeEach
    void setUp() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            var thread = new Thread(r, "astra-lights-test");
            thread.setDaemon(true);
            return thread;
        });
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        scheduler.shutdownNow();
        scheduler.awaitTermination(1, TimeUnit.SECONDS);
    }

    private TrafficLight startedLight(LightTimings timings, LightState initial) {
        var light = new TrafficLight(new Intersection("I11", 350, 300), timings, initial);
        light.scheduleNext(scheduler);
        return light;
    }

    private static void awaitTrue(BooleanSupplier condition, Duration timeout) throws InterruptedException {
        long end = System.nanoTime() + timeout.toNanos();
        while (!condition.getAsBoolean()) {
            assertTrue(System.nanoTime() < end, "condition not reached in " + timeout);
            Thread.sleep(1);
        }
    }

    private static boolean bothGreen(LightState state) {
        return state.colorFor(Axis.NS) == LightColor.GREEN && state.colorFor(Axis.EW) == LightColor.GREEN;
    }

    @Test
    void preemptTurnsGreenQuicklyAndReleaseResumesCycle() throws InterruptedException {
        var timings = new FixedTimings(Duration.ofMillis(300), Duration.ofMillis(50), Duration.ofMillis(50),
                Duration.ofSeconds(30));
        TrafficLight light = startedLight(timings, new GreenState(Axis.NS));
        List<LightState> seen = new ArrayList<>();

        long start = System.nanoTime();
        light.preempt(Axis.EW, "AMB-1");
        awaitTrue(() -> light.colorFor(Axis.EW) == LightColor.GREEN, Duration.ofMillis(MAX_PREEMPT_MILLIS));
        assertTrue(System.nanoTime() - start < TimeUnit.MILLISECONDS.toNanos(MAX_PREEMPT_MILLIS));
        assertEquals(new EmergencyState(Axis.EW), light.state());
        assertTrue(light.isPreempted());

        // Sin release, el ciclo no avanza aunque pasen varios verdes normales.
        Thread.sleep(timings.greenSeconds().toMillis() * 2);
        assertEquals(new EmergencyState(Axis.EW), light.state());

        light.release("AMB-1");
        assertEquals(new YellowState(Axis.EW), light.state());
        assertFalse(light.isPreempted());

        long end = System.nanoTime() + SAMPLING.toNanos();
        while (System.nanoTime() < end) {
            LightState state = light.state();
            assertFalse(bothGreen(state), state.name());
            if (!seen.contains(state)) {
                seen.add(state);
            }
            Thread.yield();
        }
        List<LightState> order = seen;
        assertTrue(order.indexOf(new RedState(Axis.NS)) > order.indexOf(new YellowState(Axis.EW)), order.toString());
        assertTrue(order.contains(new GreenState(Axis.NS)), "cycle did not resume: " + order);
        assertTrue(order.contains(new GreenState(Axis.EW)), "cycle did not resume: " + order);
    }

    @Test
    void preemptOnAlreadyGreenAxisHoldsTheGreen() throws InterruptedException {
        var timings = new FixedTimings(Duration.ofMillis(100), Duration.ofMillis(50), Duration.ofMillis(50),
                Duration.ofSeconds(30));
        TrafficLight light = startedLight(timings, new GreenState(Axis.EW));

        light.preempt(Axis.EW, "AMB-1");
        Thread.sleep(timings.greenSeconds().toMillis() * 3);

        assertEquals(LightColor.GREEN, light.colorFor(Axis.EW));
        light.release("AMB-1");
        assertEquals(new YellowState(Axis.EW), light.state());
    }

    @Test
    void releaseWaitsForEveryEmergencyVehicle() {
        var timings = new FixedTimings(Duration.ofSeconds(5), Duration.ofSeconds(1), Duration.ofSeconds(1),
                Duration.ofSeconds(30));
        TrafficLight light = startedLight(timings, new GreenState(Axis.NS));

        light.preempt(Axis.EW, "AMB-1");
        light.preempt(Axis.EW, "AMB-2");
        light.release("AMB-1");
        assertEquals(new EmergencyState(Axis.EW), light.state());

        light.release("AMB-2");
        assertEquals(new YellowState(Axis.EW), light.state());
    }

    @Test
    void safetyLimitEndsEmergencyOnItsOwn() throws InterruptedException {
        var timings = new FixedTimings(Duration.ofSeconds(5), Duration.ofSeconds(5), Duration.ofSeconds(1),
                Duration.ofMillis(100));
        TrafficLight light = startedLight(timings, new GreenState(Axis.NS));

        light.preempt(Axis.EW, "AMB-1");
        awaitTrue(() -> light.state().equals(new YellowState(Axis.EW)), Duration.ofSeconds(2));

        assertFalse(light.isPreempted());
        light.release("AMB-1");
        assertEquals(new YellowState(Axis.EW), light.state());
    }

    @Test
    void concurrentPreemptAndReleaseKeepInvariant() throws InterruptedException {
        var timings = new FixedTimings(Duration.ofMillis(20), Duration.ofMillis(10), Duration.ofMillis(10),
                Duration.ofMillis(30));
        TrafficLight light = startedLight(timings, new GreenState(Axis.NS));
        Queue<Throwable> errors = new ConcurrentLinkedQueue<>();
        var ready = new CountDownLatch(THREADS);
        long end = System.nanoTime() + SAMPLING.toNanos();

        List<Thread> workers = new ArrayList<>();
        for (int t = 0; t < THREADS; t++) {
            String id = "AMB-" + t;
            var worker = new Thread(() -> {
                ready.countDown();
                try {
                    ready.await();
                    var random = ThreadLocalRandom.current();
                    while (System.nanoTime() < end) {
                        if (random.nextBoolean()) {
                            light.preempt(random.nextBoolean() ? Axis.NS : Axis.EW, id);
                        } else {
                            light.release(id);
                        }
                    }
                    light.release(id);
                } catch (Throwable e) {
                    errors.add(e);
                }
            }, "astra-worker-test-" + t);
            workers.add(worker);
            worker.start();
        }

        int samples = 0;
        while (System.nanoTime() < end) {
            LightState state = light.state();
            assertFalse(bothGreen(state), state.name());
            samples++;
        }
        for (Thread worker : workers) {
            worker.join();
        }

        assertTrue(errors.isEmpty(), errors.toString());
        assertTrue(samples > 0);
        assertFalse(light.isPreempted());
        assertFalse(light.state() instanceof EmergencyState, light.state().name());
        long before = light.transitions();
        awaitTrue(() -> light.transitions() > before + 3, Duration.ofSeconds(1));
    }
}
