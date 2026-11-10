package com.astra.command;

import com.astra.model.VehicleKind;
import com.astra.traffic.Axis;
import com.astra.traffic.LightMode;

/** Fábrica legible para la UI: {@code bus.submit(Commands.spawn(VehicleKind.BUS, "RANDOM"))}. */
public final class Commands {

    private Commands() {
    }

    public static Command pause() {
        return new PauseCmd();
    }

    public static Command resume() {
        return new ResumeCmd();
    }

    public static Command togglePause() {
        return new TogglePauseCmd();
    }

    public static Command step() {
        return new StepCmd(1);
    }

    public static Command step(int steps) {
        return new StepCmd(steps);
    }

    public static Command speed(double factor) {
        return new SetSpeedCmd(factor);
    }

    /** Origen y destino al azar entre los bordes. */
    public static Command spawn(VehicleKind kind, String strategyName) {
        return new SpawnVehicleCmd(kind, strategyName, null, null);
    }

    public static Command spawn(VehicleKind kind, String strategyName, String originId, String destId) {
        return new SpawnVehicleCmd(kind, strategyName, originId, destId);
    }

    public static Command forceLight(String intersectionId, Axis axis) {
        return new ForceLightCmd(intersectionId, axis);
    }

    public static Command lightMode(LightMode mode) {
        return new SetLightModeCmd(mode);
    }
}
