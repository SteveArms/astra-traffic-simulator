package com.astra.command;

import com.astra.core.SimulationEngine;

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
