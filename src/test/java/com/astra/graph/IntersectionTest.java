package com.astra.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IntersectionTest {

    @Test
    void threeArgumentConstructorIsPlainIntersection() {
        var node = new Intersection("I00", 10, 20);

        assertEquals(NodeKind.INTERSECTION, node.kind());
        assertEquals(0, node.capacity());
        assertFalse(node.isGateway());
        assertFalse(node.isParking());
    }

    @Test
    void kindPredicates() {
        assertTrue(new Intersection("G-N0", 0, 0, NodeKind.GATEWAY, 0).isGateway());
        assertTrue(new Intersection("P00", 0, 0, NodeKind.PARKING, 8).isParking());
        assertFalse(new Intersection("J00", 0, 0, NodeKind.JUNCTION, 0).isParking());
    }

    @Test
    void parkingNeedsCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new Intersection("P", 0, 0, NodeKind.PARKING, 0));
        assertEquals(12, new Intersection("P", 0, 0, NodeKind.PARKING, 12).capacity());
    }

    @Test
    void onlyParkingHasCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new Intersection("G", 0, 0, NodeKind.GATEWAY, 3));
        assertThrows(IllegalArgumentException.class, () -> new Intersection("J", 0, 0, NodeKind.JUNCTION, -1));
    }

    @Test
    void kindIsRequired() {
        assertThrows(NullPointerException.class, () -> new Intersection("X", 0, 0, null, 0));
    }
}
