package com.astra.parking;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.Semaphore;

/**
 * Recurso con {@code capacity} lugares numerados: un {@link Semaphore} justo cuenta los libres y un arreglo de slots
 * recuerda qué {@code T} ocupa cada uno. Es el mismo patrón que las zonas de la biblioteca del informe original: el
 * semáforo decide si hay lugar sin bloquear y el arreglo, protegido por su propio monitor, asigna el lugar concreto.
 *
 * <p>Un mismo {@code item} ocupa a lo sumo un lugar: pedirlo otra vez devuelve el que ya tiene.
 *
 * @param <T> lo que ocupa un lugar (en las playas, el id del vehículo)
 * @implNote Seguro desde cualquier hilo; ningún método bloquea.
 */
public final class ResourcePool<T> {

    private final int capacity;
    private final Semaphore permits;
    // Protegido por su propio monitor; null = libre.
    private final Object[] slots;

    public ResourcePool(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1: " + capacity);
        }
        this.capacity = capacity;
        this.permits = new Semaphore(capacity, true);
        this.slots = new Object[capacity];
    }

    /**
     * Ocupa un lugar libre sin bloquear.
     *
     * @return el índice del lugar, o vacío si no hay ninguno libre
     */
    public OptionalInt tryAcquire(T item) {
        Objects.requireNonNull(item, "item");
        synchronized (slots) {
            int held = indexOf(item);
            if (held >= 0) {
                return OptionalInt.of(held);
            }
        }
        if (!permits.tryAcquire()) {
            return OptionalInt.empty();
        }
        synchronized (slots) {
            int held = indexOf(item);
            if (held >= 0) {
                // Otro hilo lo ubicó con el mismo item mientras tanto: sobra este permiso.
                permits.release();
                return OptionalInt.of(held);
            }
            for (int i = 0; i < capacity; i++) {
                if (slots[i] == null) {
                    slots[i] = item;
                    return OptionalInt.of(i);
                }
            }
        }
        // El semáforo y los slots siempre coinciden: con un permiso hay un slot libre.
        throw new IllegalStateException("permit without a free slot");
    }

    /** Libera el lugar de {@code item}; si no ocupaba ninguno, no hace nada. */
    public void release(T item) {
        Objects.requireNonNull(item, "item");
        synchronized (slots) {
            int held = indexOf(item);
            if (held < 0) {
                return;
            }
            slots[held] = null;
        }
        permits.release();
    }

    public int available() {
        return permits.availablePermits();
    }

    public int capacity() {
        return capacity;
    }

    /** Copia de los lugares en orden: el ocupante de cada uno o vacío. */
    @SuppressWarnings("unchecked")
    public List<Optional<T>> slots() {
        synchronized (slots) {
            List<Optional<T>> copy = new ArrayList<>(capacity);
            for (Object slot : slots) {
                copy.add(Optional.ofNullable((T) slot));
            }
            return List.copyOf(copy);
        }
    }

    // Llamar con el monitor de slots tomado.
    private int indexOf(T item) {
        for (int i = 0; i < capacity; i++) {
            if (item.equals(slots[i])) {
                return i;
            }
        }
        return -1;
    }
}
