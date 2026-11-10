package com.astra.core;

import java.util.Objects;

/**
 * Recibe los cambios de {@link PauseGate}. Se invoca fuera del lock de la compuerta, en el hilo que pausó o reanudó.
 */
public interface PauseListener {

    void onPause();

    void onResume();

    /** Adaptador para componentes que no conocen core: {@code PauseListener.of(lights::pause, lights::resume)}. */
    static PauseListener of(Runnable onPause, Runnable onResume) {
        Objects.requireNonNull(onPause, "onPause");
        Objects.requireNonNull(onResume, "onResume");
        return new PauseListener() {
            @Override
            public void onPause() {
                onPause.run();
            }

            @Override
            public void onResume() {
                onResume.run();
            }
        };
    }
}
