package com.astra.traffic;

import java.time.Duration;

/** Duraciones de las fases del semáforo. */
public interface LightTimings {

    Duration greenSeconds();

    Duration yellowSeconds();

    Duration allRedSeconds();

    /** Límite de seguridad de {@link EmergencyState} si nadie lo libera antes. */
    Duration emergencyMaxSeconds();
}
