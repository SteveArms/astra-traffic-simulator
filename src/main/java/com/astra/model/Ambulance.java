package com.astra.model;

import com.astra.graph.Intersection;
import com.astra.routing.RouteStrategy;
import java.util.concurrent.atomic.AtomicInteger;

/** Ambulancia ({@code AMB-N}): tiene prioridad de emergencia y pone en verde el semáforo al que se acerca. */
public final class Ambulance extends Vehicle {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    public Ambulance(VehicleSpec spec, RouteStrategy strategy, Intersection origin, Intersection destination) {
        super("AMB-" + SEQUENCE.incrementAndGet(), VehicleKind.AMBULANCE, spec, strategy, origin, destination);
    }

    @Override
    public boolean hasEmergencyPriority() {
        return true;
    }
}
