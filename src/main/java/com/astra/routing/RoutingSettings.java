package com.astra.routing;

/** Parámetros de enrutamiento; {@code SimConfig} la implementa para que {@code routing} no dependa de {@code core}. */
public interface RoutingSettings {

    /** Penalización por ocupación en {@link LeastCongestedStrategy}. */
    double congestionFactor();

    /** Variación máxima del peso en {@link RandomRouteStrategy}. */
    double randomJitter();
}
