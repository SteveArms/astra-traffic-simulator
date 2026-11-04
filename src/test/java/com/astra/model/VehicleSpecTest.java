package com.astra.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class VehicleSpecTest {

    @Test
    void positiveValuesAreAccepted() {
        assertDoesNotThrow(() -> new VehicleSpec(14, 3, 6, 12));
    }

    @Test
    void nonPositiveOrNonFiniteValuesThrow() {
        assertThrows(IllegalArgumentException.class, () -> new VehicleSpec(0, 3, 6, 12));
        assertThrows(IllegalArgumentException.class, () -> new VehicleSpec(14, -1, 6, 12));
        assertThrows(IllegalArgumentException.class, () -> new VehicleSpec(14, 3, Double.NaN, 12));
        assertThrows(IllegalArgumentException.class, () -> new VehicleSpec(14, 3, 6, Double.POSITIVE_INFINITY));
    }
}
