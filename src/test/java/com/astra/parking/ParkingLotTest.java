package com.astra.parking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.astra.graph.Intersection;
import com.astra.graph.NodeKind;
import com.astra.util.MapLoader;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ParkingLotTest {

    private static final Intersection NODE = new Intersection("P", 0, 0, NodeKind.PARKING, 2);

    @Test
    void parksUntilFullAndCountsEverything() {
        var lot = new ParkingLot(NODE);

        assertTrue(lot.tryPark("A"));
        assertTrue(lot.tryPark("B"));
        assertFalse(lot.tryPark("C"));
        assertFalse(lot.hasSpace());
        lot.leave("A");
        lot.leave("A");

        assertEquals(2, lot.entries());
        assertEquals(1, lot.rejections());
        assertEquals(1, lot.exits());
        assertEquals(1, lot.occupied());
        assertTrue(lot.holds("B"));
    }

    @Test
    void closedLotRejectsButLetsParkedLeave() {
        var lot = new ParkingLot(NODE);
        lot.tryPark("A");
        lot.setOpen(false);

        assertFalse(lot.tryPark("B"));
        assertFalse(lot.hasSpace());
        lot.leave("A");
        assertEquals(1, lot.exits());
    }

    @Test
    void onlyParkingNodes() {
        assertThrows(IllegalArgumentException.class, () -> new ParkingLot(new Intersection("I", 0, 0)));
    }

    @Test
    void directoryRegistersTheFourV2LotsAndTheirAvailability() {
        var directory = new ParkingDirectory(MapLoader.load("maps/city-3x3-v2.json").graph());

        assertEquals(Map.of("P00", 8, "P01", 10, "P10", 12, "P11", 6), directory.availability());
        assertEquals(12, directory.gateways().size());
        directory.lots().get(0).setOpen(false);
        assertEquals(0, directory.availability().get("P00"));
    }
}
