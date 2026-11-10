package com.astra.command;

import com.astra.core.SimulationEngine;

/**
 * Acción de usuario sobre la simulación (patrón Command). La UI la envía al {@link CommandBus} y el motor la
 * ejecuta; nunca modifica el mundo directamente.
 */
public interface Command {

    /**
     * Aplica el comando sobre el motor.
     *
     * @implNote Called from the astra-engine thread, always at the start of a tick (before the world updates).
     */
    void execute(SimulationEngine engine);

    /** Texto corto para el historial y los logs. */
    default String describe() {
        return getClass().getSimpleName();
    }
}
