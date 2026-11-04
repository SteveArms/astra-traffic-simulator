package com.astra.graph;

import java.util.function.ToDoubleBiFunction;

/** Heurísticos para A* sobre el grafo vial. */
public final class Heuristics {

    private Heuristics() {
    }

    /** Distancia en línea recta: admisible mientras el largo de cada calle sea ≥ la distancia entre sus extremos. */
    public static ToDoubleBiFunction<Intersection, Intersection> euclidean() {
        return Intersection::distanceTo;
    }
}
