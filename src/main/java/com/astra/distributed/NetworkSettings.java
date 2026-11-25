package com.astra.distributed;

import java.time.Duration;

/** Parámetros de {@link SimulatedNetwork}; {@code SimConfig} la implementa, así {@code distributed} no depende de core. */
public interface NetworkSettings {

    /** Latencia mínima de un mensaje (>= 0). */
    Duration latencyMin();

    /** Latencia máxima de un mensaje (>= {@link #latencyMin()}). */
    Duration latencyMax();

    /** Semilla del sorteo de latencias. */
    long networkSeed();
}
