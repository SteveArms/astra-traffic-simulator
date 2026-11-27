/**
 * Motor de simulación de paso fijo y estado del mundo.
 *
 * <p>{@link com.astra.core.SimulationEngine} corre el tick en el hilo {@code astra-engine};
 * {@link com.astra.core.WorldState} (su único dueño) lo divide en las fases PLAN, MOVE y COMMIT, con las dos
 * primeras en paralelo en {@link com.astra.core.ParallelUpdater}. Incluye las zonas de cruce con {@code Semaphore}
 * ({@link com.astra.core.IntersectionZone}), la pausa ({@link com.astra.core.PauseGate}), el spawner, las playas
 * ({@code ParkingService}), las estadísticas, la configuración ({@link com.astra.core.SimConfig}) y los
 * {@code record} inmutables del {@link com.astra.core.WorldSnapshot} que lee la UI. No importa {@code javafx.*}.
 */
package com.astra.core;
