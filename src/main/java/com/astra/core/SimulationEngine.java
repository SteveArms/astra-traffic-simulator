package com.astra.core;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Bucle de paso fijo en el hilo {@code astra-engine}: genera vehículos, actualiza el mundo y publica un
 * {@link WorldSnapshot} por tick.
 */
public final class SimulationEngine {

    public static final String THREAD_NAME = "astra-engine";

    private static final long STOP_TIMEOUT_MILLIS = 1_000;
    private static final double MILLIS_PER_SECOND = 1_000.0;

    private final WorldState world;
    private final long tickMillis;
    private final double dtSeconds;
    private final AtomicReference<WorldSnapshot> latest = new AtomicReference<>(WorldSnapshot.EMPTY);

    // volatile para que isRunning() no necesite el lock que stop() retiene.
    private volatile ScheduledExecutorService executor;
    private long tick;
    private double simTimeSeconds;

    public SimulationEngine(SimConfig config, WorldState world) {
        this(config, world, null);
    }

    /** Conecta el spawner al mundo: corre en la fase COMMIT de cada tick. */
    public SimulationEngine(SimConfig config, WorldState world, VehicleSpawner spawner) {
        this.world = Objects.requireNonNull(world, "world");
        if (spawner != null) {
            world.setSpawner(spawner);
        }
        this.tickMillis = config.tickMillis().toMillis();
        this.dtSeconds = tickMillis / MILLIS_PER_SECOND;
    }

    /** @implNote Called from the FX Application Thread. */
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

    /** Detiene astra-engine y después los astra-worker-N. @implNote Called from the FX Application Thread. */
    public synchronized void stop() {
        if (executor != null) {
            executor.shutdownNow();
            try {
                executor.awaitTermination(STOP_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            executor = null;
        }
        world.shutdown();
    }

    /** @implNote Called from tests or StepCmd while the engine is stopped. */
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

    /** @implNote Safe to call from any thread. */
    public WorldSnapshot latestSnapshot() {
        return latest.get();
    }

    private void tickSafely() {
        try {
            tick();
        } catch (Throwable t) {
            if (Thread.currentThread().isInterrupted()) {
                // stop() interrumpió el tick a mitad de una fase: no es un fallo.
                return;
            }
            // TODO: SimLogger (Día 6).
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
