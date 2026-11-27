package com.astra.command;

import com.astra.core.SimulationEngine;
import com.astra.traffic.LightMode;
import java.util.Objects;

/** Cambia el modo de todos los semáforos (FIXED o SMART); se aplica desde el próximo verde de cada uno. */
public record SetLightModeCmd(LightMode mode) implements Command {

    public SetLightModeCmd {
        Objects.requireNonNull(mode, "mode");
    }

    @Override
    public void execute(SimulationEngine engine) {
        engine.lights().setMode(mode);
    }

    @Override
    public String describe() {
        return "SetLightMode(" + mode + ")";
    }
}
