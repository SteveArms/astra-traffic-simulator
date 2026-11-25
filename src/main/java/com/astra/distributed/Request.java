package com.astra.distributed;

import java.util.Comparator;
import java.util.Objects;

/**
 * Petición de entrada a la sección crítica. El orden {@code (timestamp, processId)} es total: dos peticiones con el
 * mismo timestamp se desempatan por {@code processId}, como exige Ricart-Agrawala. Es consistente con
 * {@code equals}: {@code compareTo} devuelve 0 solo si los dos campos son iguales.
 */
public record Request(int timestamp, String processId) implements Comparable<Request> {

    private static final Comparator<Request> ORDER =
            Comparator.comparingInt(Request::timestamp).thenComparing(Request::processId);

    public Request {
        Objects.requireNonNull(processId, "processId");
        if (timestamp < 0) {
            throw new IllegalArgumentException("timestamp must be >= 0: " + timestamp);
        }
    }

    @Override
    public int compareTo(Request other) {
        return ORDER.compare(this, other);
    }
}
