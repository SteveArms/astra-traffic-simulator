package com.astra.core;

import java.util.Objects;

/**
 * Exclusión mutua de Ricart-Agrawala de una intersección en un instante; parte de {@link WorldSnapshot}.
 *
 * @param holder       vehículo que tiene la sección crítica, o {@code null} si nadie la tiene
 * @param waitingCount vehículos encolados que no la tienen
 */
public record MutexView(String intersectionId, String holder, int waitingCount) {

    public MutexView {
        Objects.requireNonNull(intersectionId, "intersectionId");
        if (waitingCount < 0) {
            throw new IllegalArgumentException("waitingCount must be >= 0: " + waitingCount);
        }
    }

    public boolean held() {
        return holder != null;
    }
}
