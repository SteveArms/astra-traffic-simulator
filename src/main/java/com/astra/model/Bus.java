package com.astra.model;

import com.astra.graph.Intersection;
import com.astra.routing.RouteStrategy;
import java.util.concurrent.atomic.AtomicInteger;

/** Bus ({@code BUS-N}): más lento y más largo que un auto; siempre cruza la ciudad. */
public final class Bus extends Vehicle {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    public Bus(VehicleSpec spec, RouteStrategy strategy, Intersection origin, Intersection destination) {
        super("BUS-" + SEQUENCE.incrementAndGet(), VehicleKind.BUS, spec, strategy, origin, destination);
    }
}
