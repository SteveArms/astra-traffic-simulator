package com.astra.command;

import com.astra.core.SimulationEngine;

/** Congela el mundo y los semáforos; el motor sigue drenando comandos. */
public record PauseCmd() implements Command {

    @Override
    public void execute(SimulationEngine engine) {
        engine.control().pauseGate().pause();
    }

    @Override
    public String describe() {
        return "Pause";
    }
}
