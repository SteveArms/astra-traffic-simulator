package com.astra.traffic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.astra.graph.Intersection;
import com.astra.graph.Road;
import org.junit.jupiter.api.Test;

class AxisTest {

    private static final Intersection CENTER = new Intersection("C", 100, 100);

    private static Road roadTo(double x, double y) {
        var target = new Intersection("T", x, y);
        return new Road("R", CENTER, target, CENTER.distanceTo(target), 10, 1);
    }

    @Test
    void horizontalRoadsAreEastWest() {
        assertEquals(Axis.EW, Axis.of(roadTo(300, 100)));
        assertEquals(Axis.EW, Axis.of(roadTo(0, 100)));
        assertEquals(Axis.EW, Axis.of(roadTo(300, 120)));
    }

    @Test
    void verticalRoadsAreNorthSouth() {
        assertEquals(Axis.NS, Axis.of(roadTo(100, 300)));
        assertEquals(Axis.NS, Axis.of(roadTo(100, 0)));
        assertEquals(Axis.NS, Axis.of(roadTo(120, 300)));
    }

    @Test
    void diagonalAt45DegreesIsEastWest() {
        assertEquals(Axis.EW, Axis.of(roadTo(200, 200)));
    }

    @Test
    void oppositeSwapsAxes() {
        assertEquals(Axis.EW, Axis.NS.opposite());
        assertEquals(Axis.NS, Axis.EW.opposite());
    }
}
