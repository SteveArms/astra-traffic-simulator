/**
 * Capa distribuida para el cruce de intersecciones: reloj de Lamport por proceso, red simulada con latencia
 * aleatoria (hilo {@code astra-net}), Ricart-Agrawala por paso de mensajes y
 * {@link com.astra.distributed.IntersectionMutex}, que agrupa un proceso por cada vía que entra a una intersección.
 * No depende de {@code core}: recibe sus parámetros por {@link com.astra.distributed.NetworkSettings}.
 */
package com.astra.distributed;
