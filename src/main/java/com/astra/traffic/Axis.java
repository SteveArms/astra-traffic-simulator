package com.astra.traffic;

import com.astra.graph.Road;

public enum Axis {
    NS,
    EW;

    public Axis opposite() {
        return this == NS ? EW : NS;
    }

    /** A 45° cuenta como EW. */
    public static Axis of(Road road) {
        double dx = road.to().x() - road.from().x();
        double dy = road.to().y() - road.from().y();
        return Math.abs(dx) >= Math.abs(dy) ? EW : NS;
    }
}
