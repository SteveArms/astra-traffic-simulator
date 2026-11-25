package com.astra.view;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ViewTransformTest {

    private static final double DELTA = 1e-9;

    @Test
    void fitsWorldInsideMarginKeepingAspectAndCentered() {
        var t = new ViewTransform();

        // 1200 x 900 en un canvas de 1000 x 600 con margen 20: limita el alto (560 / 900).
        t.fit(1000, 600, 1200, 900, 20);

        double scale = 560.0 / 900;
        assertEquals(scale, t.scale(), DELTA);
        assertEquals(20, t.toScreenY(0), DELTA);
        assertEquals(580, t.toScreenY(900), DELTA);
        assertEquals(500, t.toScreenX(600), DELTA, "the world center lands on the canvas center");
    }

    @Test
    void inverseUndoesForward() {
        var t = new ViewTransform();
        t.fit(800, 700, 700, 600, 16);

        assertEquals(123.5, t.toWorldX(t.toScreenX(123.5)), DELTA);
        assertEquals(456.25, t.toWorldY(t.toScreenY(456.25)), DELTA);
    }

    @Test
    void degenerateCanvasFallsBackToIdentity() {
        var t = new ViewTransform();
        t.fit(10, 10, 1200, 900, 20);

        assertEquals(1, t.scale(), DELTA);
        assertEquals(42, t.toScreenX(42), DELTA);
    }
}
