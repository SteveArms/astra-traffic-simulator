package com.astra.core;

/** Contadores de la red simulada en un instante; parte de {@link WorldSnapshot}. */
public record NetworkView(long sent, long delivered, long inFlight) {

    public static final NetworkView EMPTY = new NetworkView(0, 0, 0);
}
