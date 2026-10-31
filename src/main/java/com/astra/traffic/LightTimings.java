package com.astra.traffic;

import java.time.Duration;

/** Duraciones de las fases del semáforo. */
public interface LightTimings {

    Duration greenSeconds();

    Duration yellowSeconds();

    Duration allRedSeconds();
}
