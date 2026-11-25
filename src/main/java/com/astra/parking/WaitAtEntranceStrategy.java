package com.astra.parking;

import com.astra.model.Vehicle;
import java.time.Duration;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Espera en la entrada hasta {@code waitAtEntranceSeconds}; si al cumplirse sigue llena, va a la playa más cercana
 * con lugar. Recuerda qué vehículo ya esperó en qué playa.
 *
 * @implNote Solo astra-engine la usa, en la fase C: el conjunto no necesita sincronización.
 */
public final class WaitAtEntranceStrategy implements ParkingSearchStrategy {

    public static final String NAME = "WAIT_AT_ENTRANCE";
    private static final double MILLIS_PER_SECOND = 1_000.0;

    private final double waitSeconds;
    private final ParkingSearchStrategy afterWaiting = new NearestAlternativeStrategy();
    private final Set<String> waited = new HashSet<>();

    public WaitAtEntranceStrategy(Duration wait) {
        Objects.requireNonNull(wait, "wait");
        if (wait.isNegative()) {
            throw new IllegalArgumentException("wait must be >= 0: " + wait);
        }
        this.waitSeconds = wait.toMillis() / MILLIS_PER_SECOND;
    }

    @Override
    public SearchDecision onFull(Vehicle v, ParkingLot full, ParkingDirectory dir) {
        String key = v.id() + "@" + full.id();
        if (waited.add(key)) {
            return new SearchDecision.Wait(waitSeconds);
        }
        waited.remove(key);
        return afterWaiting.onFull(v, full, dir);
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void forget(String vehicleId) {
        waited.removeIf(key -> key.startsWith(vehicleId + "@"));
    }
}
