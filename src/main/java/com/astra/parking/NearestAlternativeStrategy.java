package com.astra.parking;

import com.astra.model.Vehicle;

/** Va a la playa abierta con lugar más cercana; si no queda ninguna, se rinde. */
public final class NearestAlternativeStrategy implements ParkingSearchStrategy {

    public static final String NAME = "NEAREST_ALTERNATIVE";

    @Override
    public SearchDecision onFull(Vehicle v, ParkingLot full, ParkingDirectory dir) {
        return dir.nearestWithSpace(full.node(), full)
                .<SearchDecision>map(SearchDecision.Reroute::new)
                .orElseGet(() -> GiveUpStrategy.giveUp(v, full, dir));
    }

    @Override
    public String name() {
        return NAME;
    }
}
