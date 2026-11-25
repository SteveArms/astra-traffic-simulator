package com.astra.parking;

import java.time.Duration;
import java.util.List;

/** Parámetros de las playas; {@code SimConfig} la implementa, así {@code parking} no depende de {@code core}. */
public interface ParkingSettings {

    /** Nombres válidos de {@link #parkingSearchStrategy()}. */
    List<String> STRATEGY_NAMES = List.of("NEAREST_ALTERNATIVE", "WAIT_AT_ENTRANCE", "GIVE_UP");

    /** Probabilidad (0 a 1) de que un auto nuevo vaya a estacionar en vez de cruzar la ciudad. */
    double parkingTripWeight();

    /** Estancia mínima en una playa, en tiempo simulado. */
    Duration parkingMinStay();

    /** Estancia máxima en una playa (>= la mínima). */
    Duration parkingMaxStay();

    /** Qué hace un vehículo que encuentra la playa llena; uno de {@link #STRATEGY_NAMES}. */
    String parkingSearchStrategy();

    /** Intentos fallidos de estacionar tras los cuales el vehículo se rinde y sale de la ciudad. */
    int parkingMaxSearchAttempts();

    /** Cuánto espera en la entrada la estrategia WAIT_AT_ENTRANCE antes de buscar otra playa. */
    Duration parkingWaitAtEntrance();
}
