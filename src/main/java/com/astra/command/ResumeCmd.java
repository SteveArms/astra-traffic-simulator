package com.astra.command;

import com.astra.core.SimulationEngine;

/** Reanuda el mundo, los semáforos y la red simulada; si no estaba en pausa no hace nada. */
public record ResumeCmd() implements Command {

    @Override
    public void execute(SimulationEngine engine) {
        engine.control().pauseGate().resume();
    }

    @Override
    public String describe() {
        return "Resume";
    }
}
