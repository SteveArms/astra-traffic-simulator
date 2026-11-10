package com.astra.command;

import com.astra.core.SimulationEngine;
import com.astra.traffic.Axis;
import com.astra.traffic.TrafficLight;
import java.util.Objects;
import java.util.Optional;

/** Pone en verde un eje del semáforo de una intersección; si no existe o no tiene semáforo, se ignora. */
public record ForceLightCmd(String intersectionId, Axis axis) implements Command {

    public ForceLightCmd {
        Objects.requireNonNull(intersectionId, "intersectionId");
        Objects.requireNonNull(axis, "axis");
    }

    @Override
    public void execute(SimulationEngine engine) {
        Optional<TrafficLight> light = engine.graph().findIntersection(intersectionId)
                .flatMap(engine.lights()::lightAt);
        if (light.isEmpty()) {
            engine.commandBus().markIgnored("no traffic light at " + intersectionId);
            return;
        }
        light.get().force(axis);
    }

    @Override
    public String describe() {
        return "ForceLight(" + intersectionId + ", " + axis + ")";
    }
}
