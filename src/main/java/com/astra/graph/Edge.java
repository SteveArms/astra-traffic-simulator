package com.astra.graph;

/** Arista dirigida e inmutable de un {@link Graph}. */
public interface Edge<V> {

    V from();

    V to();

    double weight();
}
