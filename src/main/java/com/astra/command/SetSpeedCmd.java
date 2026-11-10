package com.astra.command;

import com.astra.core.SimulationControl;
import com.astra.core.SimulationEngine;

/** Multiplicador de velocidad; los semáforos lo aplican desde su siguiente transición. */
public record SetSpeedCmd(double factor) implements Command {

    public SetSpeedCmd {
        // Negado para rechazar también NaN.
        if (!(factor >= SimulationControl.MIN_SPEED && factor <= SimulationControl.MAX_SPEED)) {
            throw new IllegalArgumentException("factor must be in [" + SimulationControl.MIN_SPEED + ", "
                    + SimulationControl.MAX_SPEED + "]: " + factor);
        }
    }

    @Override
    public void execute(SimulationEngine engine) {
        engine.control().setSpeed(factor);
    }

    @Override
    public String describe() {
        return "SetSpeed(×%.2f)".formatted(factor);
    }
}
