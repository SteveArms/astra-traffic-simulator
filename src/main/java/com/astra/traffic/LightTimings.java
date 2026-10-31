package com.astra.traffic;

import java.time.Duration;

/** Duraciones de cada fase del semáforo. Las implementaciones deben ser inmutables. */
public interface LightTimings {

    Duration greenSeconds();

    Duration yellowSeconds();

    /** Fase con los dos ejes en rojo entre un verde y el siguiente, para vaciar la intersección. */
    Duration allRedSeconds();
}
