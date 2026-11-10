package com.astra.command;

import com.astra.core.PauseGate;
import com.astra.core.SimulationEngine;

/** Pausa si está corriendo y reanuda si está en pausa (botón único de la UI). */
public record TogglePauseCmd() implements Command {

    @Override
    public void execute(SimulationEngine engine) {
        PauseGate gate = engine.control().pauseGate();
        if (gate.isPaused()) {
            gate.resume();
        } else {
            gate.pause();
        }
    }

    @Override
    public String describe() {
        return "TogglePause";
    }
}
