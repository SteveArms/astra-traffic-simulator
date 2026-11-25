package com.astra.graph;

/** Tipo de nodo del mapa. */
public enum NodeKind {
    /** Cruce de calles; puede tener semáforo. */
    INTERSECTION,
    /** Entrada y salida de la ciudad en el borde de la pantalla. */
    GATEWAY,
    /** Punto a mitad de cuadra, sin semáforo, del que sale la entrada de una playa. */
    JUNCTION,
    /** Playa de estacionamiento: nodo sin salida al que solo se llega por su entrada. */
    PARKING
}
