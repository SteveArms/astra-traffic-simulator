package com.astra.core;

/** Un punto de la serie temporal de {@link SimStats}. */
public record StatsPoint(double simTimeSeconds, int active, double avgWaitSeconds, double throughputPerMin) {
}
