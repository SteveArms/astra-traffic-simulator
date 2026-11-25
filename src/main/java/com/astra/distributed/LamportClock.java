package com.astra.distributed;

import com.astra.util.SimLogger;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Reloj lógico de Lamport de un proceso (una intersección o un vehículo). Cada evento se registra como
 * {@code [LAMPORT N] PROCESS -> ACTION}.
 *
 * <p>Usa un {@link ReentrantLock} en lugar de {@code AtomicInteger.updateAndGet}: los dos harían atómico el
 * {@code max(clock, remoteTs) + 1} de {@link #receive}, pero con el lock también el registro en el log queda dentro
 * de la sección crítica, así las líneas de un mismo proceso salen en el orden de su reloj. El costo es bajo:
 * {@link SimLogger#lamport} solo encola la línea y nunca bloquea en disco.
 *
 * @implNote Seguro desde cualquier hilo (astra-engine, astra-worker-N, astra-lights-N).
 */
public final class LamportClock {

    private final String processId;
    private final ReentrantLock lock = new ReentrantLock();
    private int clock;

    public LamportClock(String processId) {
        this.processId = Objects.requireNonNull(processId, "processId");
        if (processId.isBlank()) {
            throw new IllegalArgumentException("processId must not be blank");
        }
    }

    public String processId() {
        return processId;
    }

    /** Evento local: incrementa el reloj y devuelve el nuevo valor. */
    public int tick(String action) {
        return advance(0, action);
    }

    /** Envío de un mensaje: igual que {@link #tick}; el valor devuelto es el timestamp que viaja en el mensaje. */
    public int send(String action) {
        return advance(0, action);
    }

    /** Recepción de un mensaje con timestamp {@code remoteTs}: el reloj pasa a {@code max(clock, remoteTs) + 1}. */
    public int receive(int remoteTs, String action) {
        if (remoteTs < 0) {
            throw new IllegalArgumentException("remoteTs must be >= 0: " + remoteTs);
        }
        return advance(remoteTs, action);
    }

    public int current() {
        lock.lock();
        try {
            return clock;
        } finally {
            lock.unlock();
        }
    }

    private int advance(int floor, String action) {
        Objects.requireNonNull(action, "action");
        lock.lock();
        try {
            clock = Math.max(clock, floor) + 1;
            SimLogger.lamport(clock, processId, action);
            return clock;
        } finally {
            lock.unlock();
        }
    }
}
