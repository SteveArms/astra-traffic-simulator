package com.astra.command;

import com.astra.core.SimulationEngine;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Cola de comandos entre la UI (productora) y el motor (único consumidor). El motor la drena al inicio de cada tick.
 */
public final class CommandBus {

    public static final int HISTORY_SIZE = 50;

    private final LinkedBlockingQueue<Command> queue = new LinkedBlockingQueue<>();
    private final AtomicLong submitted = new AtomicLong();
    private final AtomicLong executed = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    // Protegido por su propio monitor: lo escribe astra-engine y lo lee la UI.
    private final Deque<String> history = new ArrayDeque<>(HISTORY_SIZE);

    /**
     * Encola un comando sin bloquear.
     *
     * @return {@code true} si quedó en la cola
     * @implNote Safe to call from any thread (usually the FX Application Thread).
     */
    public boolean submit(Command command) {
        Objects.requireNonNull(command, "command");
        boolean accepted = queue.offer(command);
        if (accepted) {
            submitted.incrementAndGet();
        }
        return accepted;
    }

    /**
     * Ejecuta en orden FIFO los comandos pendientes. Un comando que lanza una excepción no impide ejecutar los
     * siguientes; no cuenta como ejecutado.
     *
     * @return cuántos comandos se ejecutaron sin error
     * @implNote Called from the astra-engine thread only.
     */
    public int drainTo(SimulationEngine engine) {
        Objects.requireNonNull(engine, "engine");
        List<Command> batch = new ArrayList<>();
        queue.drainTo(batch);
        int count = 0;
        for (Command command : batch) {
            try {
                command.execute(engine);
            } catch (RuntimeException e) {
                failed.incrementAndGet();
                // TODO: SimLogger (Día 6).
                System.err.println("[" + SimulationEngine.THREAD_NAME + "] command " + command.describe()
                        + " failed: " + e);
                continue;
            }
            executed.incrementAndGet();
            count++;
            record("#" + engine.currentTick() + " " + command.describe());
        }
        return count;
    }

    /** @implNote Safe to call from any thread. Devuelve una copia, del más antiguo al más reciente. */
    public List<String> recentHistory() {
        synchronized (history) {
            return List.copyOf(history);
        }
    }

    /** @implNote Safe to call from any thread. */
    public long submittedCount() {
        return submitted.get();
    }

    /** @implNote Safe to call from any thread. */
    public long executedCount() {
        return executed.get();
    }

    /** @implNote Safe to call from any thread. */
    public long failedCount() {
        return failed.get();
    }

    /** @implNote Safe to call from any thread. */
    public int pendingCount() {
        return queue.size();
    }

    private void record(String entry) {
        synchronized (history) {
            if (history.size() == HISTORY_SIZE) {
                history.removeFirst();
            }
            history.addLast(entry);
        }
    }
}
