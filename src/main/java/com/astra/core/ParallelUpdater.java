package com.astra.core;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntConsumer;

/**
 * Ejecuta las fases PLAN y MOVE de un tick en {@code workers} hilos {@code astra-worker-N}, sincronizadas con un
 * {@link Phaser}: ningún MOVE empieza antes de que terminen todos los PLAN.
 *
 * <p>Los índices se reparten en exactamente {@code workers} bloques, uno por hilo del pool, así que todas las
 * tareas corren a la vez y ninguna espera en la barrera a una tarea sin hilo. Cada bloque hace PLAN y MOVE en el
 * mismo hilo.
 *
 * @implNote {@link #run} y {@link #shutdown} se llaman desde astra-engine (o desde el FX Thread al detener el
 *           motor); las tareas corren en astra-worker-N.
 */
public final class ParallelUpdater {

    public static final String THREAD_PREFIX = "astra-worker-";

    private static final long SHUTDOWN_TIMEOUT_MILLIS = 1_000;

    private final int workers;
    private final long phaseTimeoutNanos;
    private ExecutorService executor;

    public ParallelUpdater(int workers, Duration phaseTimeout) {
        if (workers < 1) {
            throw new IllegalArgumentException("Workers must be >= 1: " + workers);
        }
        this.workers = workers;
        this.phaseTimeoutNanos = Objects.requireNonNull(phaseTimeout, "phaseTimeout").toNanos();
    }

    public int workers() {
        return workers;
    }

    /**
     * @throws IllegalStateException si una fase no termina dentro del timeout; si una tarea falla, se relanza su
     *                               excepción (envuelta si no es unchecked) al terminar la fase
     */
    public synchronized <T> void run(List<T> items, IntConsumer planTask, IntConsumer moveTask) {
        Objects.requireNonNull(planTask, "planTask");
        Objects.requireNonNull(moveTask, "moveTask");
        int size = items.size();
        ExecutorService pool = executor();
        var failure = new AtomicReference<Throwable>();
        // El motor y todas las tareas se registran antes de enviarlas: el PLAN no puede avanzar sin ellas.
        var phaser = new Phaser(1);
        phaser.bulkRegister(workers);
        for (int w = 0; w < workers; w++) {
            int from = (int) ((long) size * w / workers);
            int to = (int) ((long) size * (w + 1) / workers);
            pool.execute(() -> runBlock(phaser, failure, from, to, planTask, moveTask));
        }
        try {
            awaitPhase(phaser, failure, "PLAN");
            awaitPhase(phaser, failure, "MOVE");
        } finally {
            // Libera a cualquier tarea que siga esperando si el motor salió por timeout o fallo.
            phaser.forceTermination();
        }
    }

    /** @implNote Called from SimulationEngine.stop(). Un {@link #run} posterior crea un pool nuevo. */
    public synchronized void shutdown() {
        if (executor == null) {
            return;
        }
        executor.shutdownNow();
        try {
            executor.awaitTermination(SHUTDOWN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        executor = null;
    }

    private ExecutorService executor() {
        if (executor == null) {
            var counter = new AtomicInteger();
            executor = Executors.newFixedThreadPool(workers, runnable -> {
                var thread = new Thread(runnable, THREAD_PREFIX + counter.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            });
        }
        return executor;
    }

    private static void runBlock(Phaser phaser, AtomicReference<Throwable> failure, int from, int to,
            IntConsumer planTask, IntConsumer moveTask) {
        boolean planned = runRange(failure, from, to, planTask);
        int phase;
        try {
            phase = phaser.awaitAdvanceInterruptibly(phaser.arrive());
        } catch (InterruptedException e) {
            // shutdownNow(): el motor ya no espera esta fase.
            Thread.currentThread().interrupt();
            return;
        }
        // Fase negativa: el motor terminó el Phaser. Con un fallo en cualquier PLAN tampoco se mueve nadie.
        if (phase >= 0 && planned && failure.get() == null) {
            runRange(failure, from, to, moveTask);
        }
        phaser.arriveAndDeregister();
    }

    private static boolean runRange(AtomicReference<Throwable> failure, int from, int to, IntConsumer task) {
        try {
            for (int i = from; i < to; i++) {
                task.accept(i);
            }
            return true;
        } catch (Throwable t) {
            failure.compareAndSet(null, t);
            return false;
        }
    }

    private void awaitPhase(Phaser phaser, AtomicReference<Throwable> failure, String name) {
        int phase = phaser.arrive();
        try {
            phaser.awaitAdvanceInterruptibly(phase, phaseTimeoutNanos, TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            throw new IllegalStateException(name + " phase did not finish in "
                    + TimeUnit.NANOSECONDS.toMillis(phaseTimeoutNanos) + " ms", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(name + " phase interrupted", e);
        }
        Throwable error = failure.get();
        if (error instanceof RuntimeException runtime) {
            throw runtime;
        }
        if (error instanceof Error fatal) {
            throw fatal;
        }
        if (error != null) {
            throw new IllegalStateException(name + " phase failed", error);
        }
    }
}
