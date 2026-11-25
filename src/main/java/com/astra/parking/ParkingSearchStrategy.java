package com.astra.parking;

import com.astra.model.Vehicle;

/**
 * Strategy: qué hace un vehículo cuando la playa a la que llegó está llena o cerrada. Una forma nueva de buscar
 * estacionamiento es una clase nueva, no un {@code if} en el motor.
 *
 * @implNote Called from astra-engine (fase C).
 */
public interface ParkingSearchStrategy {

    SearchDecision onFull(Vehicle v, ParkingLot full, ParkingDirectory dir);

    /** "NEAREST_ALTERNATIVE", "WAIT_AT_ENTRANCE" o "GIVE_UP". */
    String name();

    /** El vehículo estacionó o salió de la ciudad: la estrategia puede olvidar lo que sabía de él. */
    default void forget(String vehicleId) {
    }
}
