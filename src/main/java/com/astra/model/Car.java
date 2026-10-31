package com.astra.model;

import com.astra.graph.Road;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class Car extends Vehicle {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    public Car(List<Road> route, double maxSpeed, double acceleration, double deceleration) {
        super("CAR-" + SEQUENCE.incrementAndGet(), VehicleKind.CAR, route, maxSpeed, acceleration, deceleration);
    }
}
