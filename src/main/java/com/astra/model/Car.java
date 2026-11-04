package com.astra.model;

import com.astra.graph.Intersection;
import com.astra.routing.RouteStrategy;
import java.util.concurrent.atomic.AtomicInteger;

public final class Car extends Vehicle {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    public Car(VehicleSpec spec, RouteStrategy strategy, Intersection origin, Intersection destination) {
        super("CAR-" + SEQUENCE.incrementAndGet(), VehicleKind.CAR, spec, strategy, origin, destination);
    }
}
