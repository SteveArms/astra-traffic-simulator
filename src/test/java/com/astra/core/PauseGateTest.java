package com.astra.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(5)
class PauseGateTest {

    private final PauseGate gate = new PauseGate();

    @Test
    void awaitResumedReturnsTrueAfterResumeFromAnotherThread() throws InterruptedException {
        gate.pause();
        Thread resumer = Thread.ofPlatform().name("resumer").start(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            gate.resume();
        });

        assertTrue(gate.awaitResumed(2, TimeUnit.SECONDS));
        assertFalse(gate.isPaused());
        resumer.join();
    }

    @Test
    void awaitResumedReturnsFalseOnTimeout() throws InterruptedException {
        gate.pause();
        long start = System.nanoTime();

        assertFalse(gate.awaitResumed(150, TimeUnit.MILLISECONDS));
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) >= 150);
        assertTrue(gate.isPaused());
    }

    @Test
    void awaitResumedReturnsImmediatelyWhenNotPaused() throws InterruptedException {
        assertTrue(gate.awaitResumed(0, TimeUnit.MILLISECONDS));
    }

    @Test
    void listenersAreCalledOncePerChange() {
        var pauses = new AtomicInteger();
        var resumes = new AtomicInteger();
        gate.addListener(PauseListener.of(pauses::incrementAndGet, resumes::incrementAndGet));

        gate.pause();
        gate.pause();
        gate.pause();
        assertEquals(1, pauses.get());
        assertEquals(0, resumes.get());

        gate.resume();
        gate.resume();
        assertEquals(1, pauses.get());
        assertEquals(1, resumes.get());

        gate.pause();
        assertEquals(2, pauses.get());
    }

    @Test
    void listenersRunOutsideTheLock() throws InterruptedException {
        // Un listener que consulta la compuerta desde otro hilo se colgaría si se le notificara con el lock tomado.
        var observed = new AtomicInteger();
        gate.addListener(PauseListener.of(() -> {
            Thread probe = Thread.ofPlatform().start(() -> {
                if (gate.isPaused()) {
                    observed.incrementAndGet();
                }
            });
            try {
                probe.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, () -> { }));

        gate.pause();

        assertEquals(1, observed.get());
    }
}
