/**
 * Semáforos de dos ejes con el patrón State y su control asíncrono.
 *
 * <p>Cada fase es un {@link com.astra.traffic.LightState} inmutable ({@code GreenState}, {@code YellowState},
 * {@code RedState}, {@code EmergencyState} y {@code SmartAdaptiveState}) que decide el color de cada
 * {@link com.astra.traffic.Axis}, su duración y la fase siguiente. {@link com.astra.traffic.TrafficLight} guarda el
 * estado en un {@code AtomicReference} y {@link com.astra.traffic.LightController} programa las transiciones en los
 * hilos {@code astra-lights-N} de un {@code ScheduledExecutorService}. No depende de {@code core}: recibe los tiempos
 * por {@link com.astra.traffic.LightTimings} y {@link com.astra.traffic.SmartTimings} y la cola de cada eje por
 * {@link com.astra.traffic.QueueSensor}.
 */
package com.astra.traffic;
