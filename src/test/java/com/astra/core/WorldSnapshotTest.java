package com.astra.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.astra.model.VehicleKind;
import com.astra.model.VehicleView;
import com.astra.traffic.LightColor;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorldSnapshotTest {

    private static final VehicleView CAR = new VehicleView("CAR-1", VehicleKind.CAR, 10, 20, 0, 5, 12, "SHORTEST");
    private static final VehicleView BUS = new VehicleView("BUS-1", VehicleKind.BUS, 30, 40, Math.PI, 3, 20, "RANDOM");
    private static final LightView LIGHT =
            new LightView("I11", 350, 300, LightColor.GREEN, LightColor.RED, "GREEN(NS)", 1, 2, 0, 0);

    @Test
    void vehiclesListIsUnmodifiable() {
        var snapshot = snapshot(List.of(CAR), List.of(LIGHT));

        assertThrows(UnsupportedOperationException.class, () -> snapshot.vehicles().add(BUS));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.vehicles().clear());
    }

    @Test
    void changingOriginalListDoesNotAffectSnapshot() {
        var original = new ArrayList<>(List.of(CAR));
        var snapshot = snapshot(original, List.of());

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
        assertEquals(0, WorldSnapshot.EMPTY.lightCount());
        assertEquals(0, WorldSnapshot.EMPTY.spawned());
        assertEquals(0, WorldSnapshot.EMPTY.activeCount(VehicleKind.BUS));
    }

    @Test
    void countersAndActiveByKindAreCopied() {
        var active = new EnumMap<VehicleKind, Integer>(VehicleKind.class);
        active.put(VehicleKind.CAR, 1);
        var snapshot = new WorldSnapshot(1, 0.05, List.of(CAR), List.of(), 5, 4, active, false, 1.0, StatsView.EMPTY);

        active.put(VehicleKind.CAR, 9);

        assertEquals(5, snapshot.spawned());
        assertEquals(4, snapshot.despawned());
        assertEquals(1, snapshot.activeCount(VehicleKind.CAR));
        assertEquals(0, snapshot.activeCount(VehicleKind.AMBULANCE));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.activeByKind().clear());
    }

    @Test
    void nullsAreRejected() {
        assertThrows(NullPointerException.class,
                () -> new WorldSnapshot(1, 0, null, List.of(), 0, 0, Map.of(), false, 1.0, StatsView.EMPTY));
        assertThrows(NullPointerException.class,
                () -> new VehicleView(null, VehicleKind.CAR, 0, 0, 0, 0, 12, "SHORTEST"));
        assertThrows(NullPointerException.class,
                () -> new VehicleView("CAR-1", VehicleKind.CAR, 0, 0, 0, 0, 12, null));
    }

    @Test
    void lightsListIsCopiedAndUnmodifiable() {
        var original = new ArrayList<>(List.of(LIGHT));
        var snapshot = snapshot(List.of(), original);

        original.clear();

        assertEquals(List.of(LIGHT), snapshot.lights());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.lights().add(LIGHT));
    }

    private static WorldSnapshot snapshot(List<VehicleView> vehicles, List<LightView> lights) {
        return new WorldSnapshot(1, 0.05, vehicles, lights, 0, 0, Map.of(), false, 1.0, StatsView.EMPTY);
    }

    @Test
    void lightViewReportsZoneState() {
        assertTrue(LIGHT.zoneOccupied());
        assertFalse(LIGHT.zoneFull());
        assertTrue(new LightView("I00", 0, 0, LightColor.RED, LightColor.RED, "RED(next NS)", 2, 2, 0, 0).zoneFull());
    }
}
