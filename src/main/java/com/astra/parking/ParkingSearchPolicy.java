package com.astra.parking;

import com.astra.model.Vehicle;
import java.util.Objects;

/**
 * Aplica una {@link ParkingSearchStrategy} con el tope de intentos: tras {@code maxSearchAttempts} llegadas fallidas a
 * una playa, el vehículo siempre se rinde, sea cual sea la estrategia.
 *
 * @implNote Called from astra-engine (fase C).
 */
public final class ParkingSearchPolicy {

    private final ParkingSearchStrategy strategy;
    private final int maxSearchAttempts;

    public ParkingSearchPolicy(ParkingSearchStrategy strategy, int maxSearchAttempts) {
        this.strategy = Objects.requireNonNull(strategy, "strategy");
        if (maxSearchAttempts < 1) {
            throw new IllegalArgumentException("maxSearchAttempts must be >= 1: " + maxSearchAttempts);
        }
        this.maxSearchAttempts = maxSearchAttempts;
    }

    /** La estrategia de {@code settings}. */
    public static ParkingSearchPolicy from(ParkingSettings settings) {
        ParkingSearchStrategy strategy = switch (settings.parkingSearchStrategy()) {
            case NearestAlternativeStrategy.NAME -> new NearestAlternativeStrategy();
            case WaitAtEntranceStrategy.NAME -> new WaitAtEntranceStrategy(settings.parkingWaitAtEntrance());
            case GiveUpStrategy.NAME -> new GiveUpStrategy();
            default -> throw new IllegalArgumentException(
                    "Unknown parking strategy: " + settings.parkingSearchStrategy());
        };
        return new ParkingSearchPolicy(strategy, settings.parkingMaxSearchAttempts());
    }

    /**
     * @param failedAttempts llegadas fallidas a una playa, contando esta (>= 1)
     */
    public SearchDecision onFull(Vehicle v, ParkingLot full, ParkingDirectory dir, int failedAttempts) {
        if (failedAttempts >= maxSearchAttempts) {
            strategy.forget(v.id());
            return GiveUpStrategy.giveUp(v, full, dir);
        }
        return strategy.onFull(v, full, dir);
    }

    public void forget(String vehicleId) {
        strategy.forget(vehicleId);
    }

    public ParkingSearchStrategy strategy() {
        return strategy;
    }

    public int maxSearchAttempts() {
        return maxSearchAttempts;
    }
}
