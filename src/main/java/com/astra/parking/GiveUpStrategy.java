package com.astra.parking;

import com.astra.model.Vehicle;

/** Se rinde enseguida y sale por el gateway más cercano. */
public final class GiveUpStrategy implements ParkingSearchStrategy {

    public static final String NAME = "GIVE_UP";

    @Override
    public SearchDecision onFull(Vehicle v, ParkingLot full, ParkingDirectory dir) {
        return giveUp(v, full, dir);
    }

    @Override
    public String name() {
        return NAME;
    }

    // Sin gateways alcanzables (no pasa en los mapas con playas) sale por su destino final.
    static SearchDecision giveUp(Vehicle v, ParkingLot full, ParkingDirectory dir) {
        return new SearchDecision.GiveUp(dir.nearestGateway(full.node()).orElse(v.finalDestination()));
    }
}
