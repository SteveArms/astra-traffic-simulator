package com.astra.core;

import java.util.List;
import java.util.Objects;

/**
 * Una playa en un instante; parte de {@link WorldSnapshot}.
 *
 * @param cars autos estacionados, cada uno con el índice de su cajón (el mismo orden que dibuja la vista)
 */
public record ParkingView(String lotId, int capacity, boolean open, List<ParkedCar> cars) {

    /** Auto en el cajón {@code slot} (0 a capacity - 1). */
    public record ParkedCar(int slot, String vehicleId, double length) {
        public ParkedCar {
            Objects.requireNonNull(vehicleId, "vehicleId");
        }
    }

    public ParkingView {
        Objects.requireNonNull(lotId, "lotId");
        cars = List.copyOf(cars);
    }

    public int free() {
        return capacity - cars.size();
    }

    /** Llena o cerrada: no admite a nadie más. */
    public boolean full() {
        return !open || free() == 0;
    }
}
