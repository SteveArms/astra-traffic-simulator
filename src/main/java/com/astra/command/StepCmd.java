package com.astra.command;

import com.astra.core.SimulationEngine;

/** Avanza {@code steps} ticks con el mundo en pausa; sin pausa se ignora. */
public record StepCmd(int steps) implements Command {

    public static final int MAX_STEPS = 100;

    public StepCmd {
        if (steps < 1 || steps > MAX_STEPS) {
            throw new IllegalArgumentException("steps must be in [1, " + MAX_STEPS + "]: " + steps);
        }
    }

    @Override
    public void execute(SimulationEngine engine) {
        if (!engine.control().pauseGate().isPaused()) {
            engine.commandBus().markIgnored("not paused");
            return;
        }
        engine.control().requestSteps(steps);
    }

    @Override
    public String describe() {
        return "Step(" + steps + ")";
    }
}
