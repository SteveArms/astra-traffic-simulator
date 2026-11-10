package com.astra.core;

import com.astra.command.CommandBus;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Bucle de paso fijo en el hilo {@code astra-engine}: ejecuta los comandos pendientes, genera vehículos, actualiza
 * el mundo y publica un {@link WorldSnapshot} por tick. Tanto el bucle como {@link #step()} corren en ese hilo.
 *
 * <p>En pausa el motor no se bloquea: sigue drenando comandos (para poder recibir la reanudación) y publicando
 * snapshots, pero no actualiza el mundo salvo que haya pasos pedidos con {@link SimulationControl#requestStep()}.
 */
public final class SimulationEngine {

    public static final String THREAD_NAME = "astra-engine";

    private static final long STOP_TIMEOUT_MILLIS = 1_000;
    private static final double MILLIS_PER_SECOND = 1_000.0;

    private final WorldState world;
    private final CommandBus commandBus;
    private final SimulationControl control;
    private final long tickMillis;
    private final double dtSeconds;
    private final AtomicReference<WorldSnapshot> latest = new AtomicReference<>(WorldSnapshot.EMPTY);

    // Guardados por el monitor de this; volatile para que isRunning() no necesite el lock que stop() retiene.
    private volatile ScheduledExecutorService executor;
    private volatile ScheduledFuture<?> loop;
    private volatile Thread engineThread;
    // Solo los toca astra-engine.
    private long tick;
    private double simTimeSeconds;

    public SimulationEngine(SimConfig config, WorldState world) {
        this(config, world, null);
    }

    /** Conecta el spawner al mundo: corre en la fase COMMIT de cada tick. */
    public SimulationEngine(SimConfig config, WorldState world, VehicleSpawner spawner) {
        this(config, world, spawner, new CommandBus());
    }

    public SimulationEngine(SimConfig config, WorldState world, VehicleSpawner spawner, CommandBus commandBus) {
        this(config, world, spawner, commandBus, new SimulationControl());
    }

    public SimulationEngine(SimConfig config, WorldState world, VehicleSpawner spawner, CommandBus commandBus,
            SimulationControl control) {
        this.world = Objects.requireNonNull(world, "world");
        this.commandBus = Objects.requireNonNull(commandBus, "commandBus");
        this.control = Objects.requireNonNull(control, "control");
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
        loop = engineExecutor().scheduleAtFixedRate(this::tickSafely, 0, tickMillis, TimeUnit.MILLISECONDS);
    }

    /** Detiene astra-engine y después los astra-worker-N. @implNote Called from the FX Application Thread. */
    public synchronized void stop() {
        loop = null;
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

    /**
     * Avanza un tick en el hilo astra-engine y espera a que termine.
     *
     * @implNote Called from tests or StepCmd while the engine is stopped.
     */
    public synchronized void step() {
        if (isRunning()) {
            throw new IllegalStateException("Cannot step while the engine is running");
        }
        if (Thread.currentThread() == engineThread) {
            tick();
            return;
        }
        try {
            engineExecutor().submit(this::tick).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while stepping", e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (e.getCause() instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(e.getCause());
        }
    }

    /** @implNote Safe to call from any thread. */
    public boolean isRunning() {
        ScheduledFuture<?> current = loop;
        return current != null && !current.isDone();
    }

    /** @implNote Safe to call from any thread. */
    public WorldSnapshot latestSnapshot() {
        return latest.get();
    }

    /** @implNote Safe to call from any thread. */
    public CommandBus commandBus() {
        return commandBus;
    }

    /** @implNote Safe to call from any thread. */
    public SimulationControl control() {
        return control;
    }

    /** Ticks completados. @implNote Called from the astra-engine thread (p. ej. desde un Command). */
    public long currentTick() {
        return tick;
    }

    private ScheduledExecutorService engineExecutor() {
        if (executor == null || executor.isShutdown()) {
            executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
                var thread = new Thread(runnable, THREAD_NAME);
                thread.setDaemon(true);
                engineThread = thread;
                return thread;
            });
        }
        return executor;
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
        commandBus.drainTo(this);
        boolean paused = control.pauseGate().isPaused();
        double speed = control.speedFactor();
        if (paused && !control.tryConsumeStep()) {
            tick++;
            latest.set(world.toSnapshot(tick, simTimeSeconds, true, speed));
            return;
        }
        double dt = dtSeconds * speed;
        world.update(dt);
        tick++;
        simTimeSeconds += dt;
        latest.set(world.toSnapshot(tick, simTimeSeconds, paused, speed));
    }
}
