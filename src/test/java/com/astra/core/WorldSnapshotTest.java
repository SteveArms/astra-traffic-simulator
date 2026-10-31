package com.astra.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.astra.model.VehicleKind;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorldSnapshotTest {

    private static final VehicleView CAR = new VehicleView("CAR-1", VehicleKind.CAR, 10, 20, 0, 5);
    private static final VehicleView BUS = new VehicleView("BUS-1", VehicleKind.BUS, 30, 40, Math.PI, 3);

    @Test
    void vehiclesListIsUnmodifiable() {
        var snapshot = new WorldSnapshot(1, 0.05, List.of(CAR));

        assertThrows(UnsupportedOperationException.class, () -> snapshot.vehicles().add(BUS));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.vehicles().clear());
    }

    @Test
    void changingOriginalListDoesNotAffectSnapshot() {
        var original = new ArrayList<>(List.of(CAR));
        var snapshot = new WorldSnapshot(1, 0.05, original);

        original.add(BUS);
        original.remove(CAR);

        assertEquals(List.of(CAR), snapshot.vehicles());
        assertEquals(1, snapshot.vehicleCount());
    }

    @Test
    void emptySnapshotHasNoVehicles() {
        assertEquals(0, WorldSnapshot.EMPTY.tick());
        assertEquals(0, WorldSnapshot.EMPTY.simTimeSeconds());
        assertEquals(0, WorldSnapshot.EMPTY.vehicleCount());
        assertTrue(WorldSnapshot.EMPTY.vehicles().isEmpty());
    }

    @Test
    void nullsAreRejected() {
        assertThrows(NullPointerException.class, () -> new WorldSnapshot(1, 0, null));
        assertThrows(NullPointerException.class, () -> new VehicleView(null, VehicleKind.CAR, 0, 0, 0, 0));
    }
}
