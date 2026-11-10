package com.astra.traffic;

import com.astra.graph.Intersection;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.DoubleSupplier;

/**
 * Semáforo de una intersección; su estado se lee desde cualquier hilo sin bloquear.
 *
 * <p>Los cambios de estado ({@link #transition}, {@link #preempt}, {@link #release}, {@link #scheduleNext})
 * se serializan con un {@link ReentrantLock} privado. Orden de bloqueo: este lock es siempre el último que se
 * toma; con él tomado solo se llama a {@code schedule}/{@code cancel} del executor, que nunca vuelven a entrar
 * al semáforo. Quien lo llama no debe tener tomado otro lock que astra-lights pueda pedir.
 *
 * <p>Pausa: {@link #pause} cancela la tarea programada y guarda el tiempo que le faltaba; {@link #resume} la
 * reprograma con ese tiempo. Mientras está en pausa, {@link #preempt} y {@link #release} cambian el estado y solo
 * guardan la duración de la nueva fase. La velocidad divide cada duración al programarla: un cambio de velocidad
 * se aplica desde la siguiente transición, no a la que ya está en curso.
 *
 * @implNote {@link #transition} corre en astra-lights-N; {@link #preempt} y {@link #release} en astra-engine o
 *           astra-worker-N; {@link #pause} y {@link #resume} en el hilo que pausa (normalmente astra-engine).
 */
public final class TrafficLight {

    private final Intersection intersection;
    private final LightTimings timings;
    private final DoubleSupplier speedFactor;
    private final AtomicReference<LightState> state;
    private final AtomicLong transitions = new AtomicLong();
    private final AtomicReference<ScheduledFuture<?>> pending = new AtomicReference<>();
    private final Set<String> preemptors = ConcurrentHashMap.newKeySet();
    private final ReentrantLock lock = new ReentrantLock();
    private volatile ScheduledExecutorService executor;
    // Invalida una transición que ya empezó a correr cuando se canceló su tarea. Protegido por lock.
    private long generation;
    // Protegidos por lock. remainingMillis < 0: no había nada programado al pausar.
    private boolean paused;
    private long remainingMillis = -1;

    public TrafficLight(Intersection intersection, LightTimings timings, LightState initial) {
        this(intersection, timings, initial, () -> 1.0);
    }

    /** @param speedFactor multiplicador de velocidad (> 0); se lee al programar cada transición */
    public TrafficLight(Intersection intersection, LightTimings timings, LightState initial,
            DoubleSupplier speedFactor) {
        this.intersection = Objects.requireNonNull(intersection, "intersection");
        this.timings = Objects.requireNonNull(timings, "timings");
        this.state = new AtomicReference<>(Objects.requireNonNull(initial, "initial"));
        this.speedFactor = Objects.requireNonNull(speedFactor, "speedFactor");
    }

    public LightState state() {
        return state.get();
    }

    public LightColor colorFor(Axis axis) {
        return state.get().colorFor(axis);
    }

    public boolean canPass(Axis axis) {
        return state.get().canPass(axis);
    }

    public long transitions() {
        return transitions.get();
    }

    public Intersection intersection() {
        return intersection;
    }

    public boolean isPreempted() {
        return !preemptors.isEmpty();
    }

    public boolean isPaused() {
        lock.lock();
        try {
            return paused;
        } finally {
            lock.unlock();
        }
    }

    /** Congela la fase actual guardando lo que le falta. Idempotente. */
    public void pause() {
        lock.lock();
        try {
            if (paused) {
                return;
            }
            ScheduledFuture<?> current = pending.get();
            remainingMillis = current == null ? -1 : Math.max(0, current.getDelay(TimeUnit.MILLISECONDS));
            cancelPendingLocked();
            paused = true;
        } finally {
            lock.unlock();
        }
    }

    /** Reprograma la fase con el tiempo que le faltaba al pausar. Idempotente. */
    public void resume() {
        lock.lock();
        try {
            if (!paused) {
                return;
            }
            paused = false;
            if (remainingMillis >= 0) {
                scheduleLocked(remainingMillis);
            }
            remainingMillis = -1;
        } finally {
            lock.unlock();
        }
    }

    /** Solo para tests, con el controlador detenido. */
    void forceState(LightState forced) {
        Objects.requireNonNull(forced, "forced");
        lock.lock();
        try {
            state.set(forced);
        } finally {
            lock.unlock();
        }
    }

    /** @implNote Called from LightController.start(). */
    public void scheduleNext(ScheduledExecutorService ex) {
        Objects.requireNonNull(ex, "executor");
        lock.lock();
        try {
            executor = ex;
            cancelPendingLocked();
            scheduleLocked();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Pone en verde {@code axis} hasta que todos los vehículos que lo pidieron llamen a {@link #release}.
     * Si otro eje estaba en emergencia, gana la última petición.
     *
     * @implNote Called from astra-engine or astra-worker-N.
     */
    public void preempt(Axis axis, String vehicleId) {
        Objects.requireNonNull(axis, "axis");
        Objects.requireNonNull(vehicleId, "vehicleId");
        lock.lock();
        try {
            cancelPendingLocked();
            var emergency = new EmergencyState(axis);
            if (!state.get().equals(emergency)) {
                state.set(emergency);
                transitions.incrementAndGet();
            }
            preemptors.add(vehicleId);
            scheduleLocked();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Cuando se libera el último vehículo, el ciclo normal sigue desde el amarillo del eje en emergencia.
     *
     * @implNote Called from astra-engine or astra-worker-N.
     */
    public void release(String vehicleId) {
        Objects.requireNonNull(vehicleId, "vehicleId");
        lock.lock();
        try {
            if (!preemptors.remove(vehicleId) || !preemptors.isEmpty()) {
                return;
            }
            LightState current = state.get();
            if (current instanceof EmergencyState) {
                cancelPendingLocked();
                state.set(current.next());
                transitions.incrementAndGet();
                scheduleLocked();
            }
        } finally {
            lock.unlock();
        }
    }

    private void transition(long scheduledGeneration) {
        lock.lock();
        try {
            if (scheduledGeneration != generation) {
                return;
            }
            pending.set(null);
            try {
                LightState current = state.get();
                state.set(current.next());
                transitions.incrementAndGet();
                if (current instanceof EmergencyState) {
                    // Venció el límite de seguridad: las peticiones pendientes ya no cuentan.
                    preemptors.clear();
                }
            } catch (RuntimeException e) {
                // TODO: SimLogger (Día 6).
                System.err.println("[astra-lights] " + intersection.id() + " transition failed: " + e);
            }
            scheduleLocked();
        } finally {
            lock.unlock();
        }
    }

    private void cancelPendingLocked() {
        generation++;
        ScheduledFuture<?> previous = pending.getAndSet(null);
        if (previous != null) {
            previous.cancel(false);
        }
    }

    // Sin executor (controlador sin iniciar) el estado cambia pero no se programa la siguiente transición.
    private void scheduleLocked() {
        double speed = speedFactor.getAsDouble();
        scheduleLocked(Math.round(state.get().duration(timings).toMillis() / speed));
    }

    private void scheduleLocked(long millis) {
        ScheduledExecutorService ex = executor;
        if (paused) {
            remainingMillis = ex == null ? -1 : millis;
            return;
        }
        if (ex == null) {
            return;
        }
        long scheduledGeneration = ++generation;
        try {
            pending.set(ex.schedule(() -> transition(scheduledGeneration), millis, TimeUnit.MILLISECONDS));
        } catch (RejectedExecutionException e) {
            // El controlador se detuvo.
        }
    }
}
