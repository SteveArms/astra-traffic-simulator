package com.astra.core;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Estado de pausa de la simulación. El motor solo la consulta ({@link #isPaused()}) y nunca espera en ella: si se
 * bloqueara, no podría drenar el {@code ResumeCmd} que la reanuda.
 *
 * <p>Los listeners se invocan <b>fuera</b> del lock: un listener (p. ej. los semáforos) toma su propio lock, y si
 * lo hiciera con este tomado, un hilo que tenga el lock del semáforo y consulte la compuerta formaría un ciclo de
 * espera. Consecuencia: dos llamadas concurrentes a pause()/resume() pueden notificar en otro orden que el de sus
 * cambios; en la práctica ambas llegan desde astra-engine (comandos), que las serializa.
 */
public final class PauseGate {

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition resumed = lock.newCondition();
    private final List<PauseListener> listeners = new CopyOnWriteArrayList<>();
    // Protegido por lock.
    private boolean paused;

    /** Idempotente: solo notifica si cambia el estado. @implNote Safe to call from any thread. */
    public void pause() {
        lock.lock();
        try {
            if (paused) {
                return;
            }
            paused = true;
        } finally {
            lock.unlock();
        }
        listeners.forEach(PauseListener::onPause);
    }

    /** Idempotente: solo notifica si cambia el estado. @implNote Safe to call from any thread. */
    public void resume() {
        lock.lock();
        try {
            if (!paused) {
                return;
            }
            paused = false;
            resumed.signalAll();
        } finally {
            lock.unlock();
        }
        listeners.forEach(PauseListener::onResume);
    }

    /** @implNote Safe to call from any thread. */
    public boolean isPaused() {
        lock.lock();
        try {
            return paused;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Espera a que se reanude. Solo para hilos que pueden bloquearse (tests, workers externos), nunca astra-engine.
     *
     * @return {@code false} si venció el timeout sin reanudarse
     */
    public boolean awaitResumed(long timeout, TimeUnit unit) throws InterruptedException {
        long nanos = unit.toNanos(timeout);
        lock.lock();
        try {
            while (paused) {
                if (nanos <= 0) {
                    return false;
                }
                nanos = resumed.awaitNanos(nanos);
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    public void addListener(PauseListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void removeListener(PauseListener listener) {
        listeners.remove(listener);
    }
}
