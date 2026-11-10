package com.astra.traffic;

import java.time.Duration;

/** Parámetros de {@link SmartAdaptiveState}; {@code SimConfig} la implementa. */
public interface SmartTimings {

    /** Para semáforos sin configuración SMART: nunca extiende. */
    SmartTimings NEVER_EXTEND = new SmartTimings() {
        @Override
        public int smartQueueThreshold() {
            return Integer.MAX_VALUE;
        }

        @Override
        public Duration smartExtensionStepSeconds() {
            return Duration.ofSeconds(1);
        }

        @Override
        public Duration smartMaxExtensionSeconds() {
            return Duration.ZERO;
        }
    };

    int smartQueueThreshold();

    Duration smartExtensionStepSeconds();

    Duration smartMaxExtensionSeconds();
}
