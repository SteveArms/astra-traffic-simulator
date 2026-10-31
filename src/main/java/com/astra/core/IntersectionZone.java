package com.astra.core;

import com.astra.graph.Intersection;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * Zona de cruce de una intersección con capacidad limitada por un {@link Semaphore}.
 *
 * @implNote Nunca usar {@code acquire()} bloqueante dentro de un tick: congelaría el motor y
 * podría causar deadlocks. Solo {@link #tryEnter(String)}.
 */
public final class IntersectionZone {

    private final Intersection intersection;
    private final int capacity;
    private final Semaphore permits;
    private final Set<String> occupants = ConcurrentHashMap.newKeySet();

    public IntersectionZone(Intersection intersection, int capacity) {
        this.intersection = Objects.requireNonNull(intersection, "intersection");
        if (capacity < 1) {
            throw new IllegalArgumentException("Capacity must be >= 1: " + capacity);
        }
        this.capacity = capacity;
        this.permits = new Semaphore(capacity, true);
    }

    /** @implNote Called from astra-engine or astra-worker-N; never blocks. */
    public boolean tryEnter(String vehicleId) {
        if (occupants.contains(vehicleId)) {
            return true;
        }
        if (!permits.tryAcquire()) {
            return false;
        }
        if (!occupants.add(vehicleId)) {
            // Otro hilo lo agregó a la vez: devolver el permiso sobrante.
            permits.release();
        }
        return true;
    }

    /** Idempotente: solo libera si el vehículo estaba dentro. */
    public void leave(String vehicleId) {
        if (occupants.remove(vehicleId)) {
            permits.release();
        }
    }

    public int occupancy() {
        return occupants.size();
    }

    public int capacity() {
        return capacity;
    }

    public boolean isFull() {
        return permits.availablePermits() == 0;
    }

    public boolean contains(String vehicleId) {
        return occupants.contains(vehicleId);
    }

    public Intersection intersection() {
        return intersection;
    }
}
