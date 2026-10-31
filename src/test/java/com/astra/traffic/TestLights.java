package com.astra.traffic;

/** Acceso de tests de otros paquetes a {@link TrafficLight#forceState(LightState)}. */
public final class TestLights {

    private TestLights() {
    }

    public static void force(TrafficLight light, LightState state) {
        light.forceState(state);
    }
}
