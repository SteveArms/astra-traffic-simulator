package com.astra.core;

/** Cómo se reserva la zona central de cada intersección. */
public enum MutexMode {
    /** Solo el {@link java.util.concurrent.Semaphore} de capacidad de {@link IntersectionZone} (Día 5). */
    SEMAPHORE,
    /** Ricart-Agrawala por mensajes entre los accesos; el semáforo de capacidad queda como segunda capa. */
    RICART_AGRAWALA
}
