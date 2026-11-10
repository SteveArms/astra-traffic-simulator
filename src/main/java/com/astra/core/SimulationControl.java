package com.astra.core;

import java.util.concurrent.atomic.AtomicInteger;

/** Controles de usuario sobre el motor: pausa, velocidad y pasos sueltos en pausa. Seguro desde cualquier hilo. */
public final class SimulationControl {

    public static final double MIN_SPEED = 0.25;
    public static final double MAX_SPEED = 4.0;

    private final PauseGate pauseGate = new PauseGate();
    private final AtomicInteger pendingSteps = new AtomicInteger();
    private volatile double speedFactor = 1.0;

    public PauseGate pauseGate() {
        return pauseGate;
    }

    public double speedFactor() {
        return speedFactor;
    }

    /** Fuera de [{@value #MIN_SPEED}, {@value #MAX_SPEED}] se ajusta al límite más cercano. */
    public void setSpeed(double factor) {
        if (Double.isNaN(factor)) {
            throw new IllegalArgumentException("speed factor is NaN");
        }
        speedFactor = Math.clamp(factor, MIN_SPEED, MAX_SPEED);
    }

    /** Pide un tick más con el mundo en pausa. */
    public void requestStep() {
        pendingSteps.incrementAndGet();
    }

    public int pendingSteps() {
        return pendingSteps.get();
    }

    /** @implNote Called from the astra-engine thread. */
    boolean tryConsumeStep() {
        return pendingSteps.getAndUpdate(n -> n > 0 ? n - 1 : 0) > 0;
    }
}
