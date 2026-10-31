package com.astra.traffic;

import com.astra.graph.Road;

/** Eje de circulación que controla un semáforo: norte-sur o este-oeste. */
public enum Axis {
    NS,
    EW;

    public Axis opposite() {
        return this == NS ? EW : NS;
    }

    /** Eje de la vía según su dirección dominante; las diagonales a 45° cuentan como EW. */
    public static Axis of(Road road) {
        double dx = road.to().x() - road.from().x();
        double dy = road.to().y() - road.from().y();
        return Math.abs(dx) >= Math.abs(dy) ? EW : NS;
    }
}
