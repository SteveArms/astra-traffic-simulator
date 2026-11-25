package com.astra.command;

import com.astra.core.MutexMode;
import com.astra.core.SimulationEngine;
import java.util.Objects;

/**
 * Cambia en vivo cómo se reserva la zona central de las intersecciones. Al cambiar, el motor cancela todas las
 * peticiones de Ricart-Agrawala pendientes entre dos ticks, así ningún vehículo queda esperando una sección que ya
 * nadie va a conceder.
 */
public record SetMutexModeCmd(MutexMode mode) implements Command {

    public SetMutexModeCmd {
        Objects.requireNonNull(mode, "mode");
    }

    @Override
    public void execute(SimulationEngine engine) {
        if (mode == MutexMode.RICART_AGRAWALA && !engine.hasDistributedLayer()) {
            engine.commandBus().markIgnored("no distributed layer");
            return;
        }
        if (!engine.setMutexMode(mode)) {
            engine.commandBus().markIgnored("already " + mode);
        }
    }

    @Override
    public String describe() {
        return "SetMutexMode(" + mode + ")";
    }
}
