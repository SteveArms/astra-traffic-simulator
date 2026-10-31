package com.astra.core;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Bucle de simulación de paso fijo en el hilo daemon {@code astra-engine}. Publica un
 * {@link WorldSnapshot} inmutable al final de cada tick.
 *
 * <p>{@code start}, {@code stop} y {@code step} están sincronizados entre sí, así que pueden
 * llamarse desde el FX Application Thread o desde un test.
 */
public final class SimulationEngine {

    public static final String THREAD_NAME = "astra-engine";

    private static final long STOP_TIMEOUT_MILLIS = 1_000;
    private static final double MILLIS_PER_SECOND = 1_000.0;

    private final WorldState world;
    private final long tickMillis;
    private final double dtSeconds;
    private final AtomicReference<WorldSnapshot> latest = new AtomicReference<>(WorldSnapshot.EMPTY);

    // volatile: isRunning() no toma el lock, así astra-engine puede consultarlo mientras stop() espera.
    private volatile ScheduledExecutorService executor;
    // Solo los modifica tick(): en astra-engine, o en el hilo de step() con el motor detenido.
    private long tick;
    private double simTimeSeconds;

    public SimulationEngine(SimConfig config, WorldState world) {
        this.world = Objects.requireNonNull(world, "world");
        this.tickMillis = config.tickMillis().toMillis();
        this.dtSeconds = tickMillis / MILLIS_PER_SECOND;
    }

    /**
     * Arranca el bucle. Tras {@link #stop()} se puede volver a arrancar.
     *
     * @throws IllegalStateException si ya está corriendo
     * @implNote Called from the FX Application Thread or a test thread.
     */
    public synchronized void start() {
        if (isRunning()) {
            throw new IllegalStateException("Engine is already running");
        }
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            var thread = new Thread(runnable, THREAD_NAME);
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleAtFixedRate(this::tickSafely, 0, tickMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * Detiene el bucle y espera hasta 1 s a que termine el tick en curso. Es idempotente.
     *
     * @implNote Called from the FX Application Thread (AstraApp.stop) or a test thread.
     */
    public synchronized void stop() {
        if (executor == null) {
            return;
        }
        executor.shutdownNow();
        try {
            executor.awaitTermination(STOP_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        executor = null;
    }

    /**
     * Ejecuta un tick de forma síncrona en el hilo que llama.
     *
     * @throws IllegalStateException si el motor está corriendo
     * @implNote Called from a test thread, or later from StepCmd while the engine is paused.
     */
    public synchronized void step() {
        if (isRunning()) {
            throw new IllegalStateException("Cannot step while the engine is running");
        }
        tick();
    }

    /** @implNote Safe to call from any thread. */
    public boolean isRunning() {
        ScheduledExecutorService current = executor;
        return current != null && !current.isShutdown();
    }

    /** @implNote Safe to call from any thread; the FX AnimationTimer reads it every frame. */
    public WorldSnapshot latestSnapshot() {
        return latest.get();
    }

    private void tickSafely() {
        try {
            tick();
        } catch (Throwable t) {
            // Si la excepción escapara, scheduleAtFixedRate cancelaría el bucle sin avisar.
            // TODO: registrar con SimLogger (Día 6).
            System.err.println("[" + THREAD_NAME + "] tick " + tick + " failed: " + t);
            t.printStackTrace();
        }
    }

    private void tick() {
        world.update(dtSeconds);
        tick++;
        simTimeSeconds += dtSeconds;
        latest.set(world.toSnapshot(tick, simTimeSeconds));
    }
}
