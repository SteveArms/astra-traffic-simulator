package com.astra.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.astra.graph.Intersection;
import com.astra.graph.NodeKind;
import java.util.List;
import org.junit.jupiter.api.Test;

class LotGeometryTest {

    private static final double DELTA = 1e-9;
    // Entrada desde el norte: la playa se extiende hacia abajo (+y).
    private static final Intersection JUNCTION = new Intersection("J", 100, 0, NodeKind.JUNCTION, 0);
    private static final Intersection PARKING = new Intersection("P", 100, 70, NodeKind.PARKING, 5);

    @Test
    void oneStallPerSpotSplitInTwoRows() {
        var lot = new LotGeometry(PARKING, JUNCTION);

        assertEquals(5, lot.stalls().size());
        assertEquals(3, lot.sideA());
        assertEquals(2, lot.sideB());
    }

    @Test
    void stallsFollowTheSlotOrderFromTheEntrance() {
        List<LotGeometry.Stall> stalls = new LotGeometry(PARKING, JUNCTION).stalls();

        // Fila A (índices 0-2) y fila B (3-4), cada una desde la entrada hacia adentro.
        double rowA = stalls.get(0).x();
        double rowB = stalls.get(3).x();
        assertTrue(rowA != rowB);
        for (int i = 1; i < 3; i++) {
            assertEquals(rowA, stalls.get(i).x(), DELTA);
            assertEquals(LotGeometry.STALL_WIDTH, stalls.get(i).y() - stalls.get(i - 1).y(), DELTA);
        }
        assertEquals(rowB, stalls.get(4).x(), DELTA);
        assertEquals(stalls.get(0).y(), stalls.get(3).y(), DELTA);
        assertTrue(stalls.get(0).y() > PARKING.y(), "the first stall is just inside the entrance");
    }

    @Test
    void everyStallIsInsideTheLotAndFacesTheAisle() {
        var lot = new LotGeometry(PARKING, JUNCTION);
        double[][] outline = lot.outline();
        double minX = Math.min(outline[0][0], outline[0][2]);
        double maxX = Math.max(outline[0][0], outline[0][2]);
        double minY = Math.min(outline[1][0], outline[1][2]);
        double maxY = Math.max(outline[1][0], outline[1][2]);

        for (LotGeometry.Stall stall : lot.stalls()) {
            assertTrue(stall.x() > minX && stall.x() < maxX && stall.y() > minY && stall.y() < maxY,
                    stall.toString());
            // Avanzar medio cajón hacia el frente acerca el auto al eje del pasillo (x = 100).
            double frontX = stall.x() + Math.cos(stall.headingRad()) * LotGeometry.STALL_DEPTH / 2;
            assertTrue(Math.abs(frontX - PARKING.x()) < Math.abs(stall.x() - PARKING.x()), stall.toString());
        }
    }

    @Test
    void lotSizeComesFromTheCapacity() {
        var lot = new LotGeometry(PARKING, JUNCTION);

        assertEquals(2 * LotGeometry.STALL_DEPTH + LotGeometry.AISLE, lot.width(), DELTA);
        assertEquals(3 * LotGeometry.STALL_WIDTH + 2 * LotGeometry.LOT_PAD, lot.depth(), DELTA);
    }
}
