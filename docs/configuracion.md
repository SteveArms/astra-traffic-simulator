# Configuración

Todos los parámetros de la simulación están en `src/main/resources/com/astra/config.properties`.
`SimConfig.load()` lo lee una vez al arrancar y crea un objeto inmutable.

- Una clave **ausente** usa el valor por defecto que tiene `SimConfig` en el código.
- Un valor **inválido** lanza `IllegalArgumentException` con el nombre de la clave, por ejemplo
  `Invalid value for 'lights.greenSeconds': 0 must be > 0`.
- Los nombres de enum y de estrategia no distinguen mayúsculas.
- Para cambiar un valor, edita el archivo y vuelve a ejecutar `mvn javafx:run`.

En las tablas, "Valor" es el que trae `config.properties`. Si el valor por defecto del código es
distinto, se indica en la columna "Validación y notas".

Tipos de validación:

| Validación | Significa |
| --- | --- |
| entero > 0 | Número entero mayor que 0 |
| entero >= 0 | Número entero mayor o igual que 0 |
| decimal > 0 | Número finito mayor que 0 |
| decimal >= 0 | Número finito mayor o igual que 0 |
| booleano | `true` o `false` |

## Motor (`engine.*`)

| Clave | Valor | Unidad | Validación y notas | Efecto |
| --- | --- | --- | --- | --- |
| `engine.tickMillis` | 50 | ms | entero > 0 | Duración real de un tick del bucle de `astra-engine`; también fija el timeout de cada fase (2 × tick) |
| `engine.parallel` | true | — | booleano | `true`: las fases PLAN y MOVE corren en `astra-worker-N`; `false`: en un bucle secuencial (para depurar y comparar) |
| `engine.workers` | 4 | hilos | entero >= 0 | Hilos `astra-worker-N`. Con 0 usa `availableProcessors() − 1`, mínimo 1 |

## Semáforos (`lights.*`, `smart.*`, `emergency.*`)

| Clave | Valor | Unidad | Validación y notas | Efecto |
| --- | --- | --- | --- | --- |
| `lights.greenSeconds` | 8 | s | entero > 0 | Duración de `GreenState` y del verde base de `SmartAdaptiveState` |
| `lights.yellowSeconds` | 2 | s | entero > 0 | Duración de `YellowState` |
| `lights.allRedSeconds` | 1 | s | entero > 0 | Duración de `RedState`, con los dos ejes en rojo |
| `smart.queueThreshold` | 5 | vehículos | entero > 0 | Cola mínima del eje en verde para extenderlo (y la del contrario debe ser menor) |
| `smart.maxExtensionSeconds` | 6 | s | entero > 0 | Extensión total máxima de un verde en modo SMART |
| `smart.extensionStepSeconds` | 2 | s | entero > 0 | Duración de cada extensión |
| `smart.queueRadius` | 120 | px | decimal > 0 | Distancia al final de la vía dentro de la cual un vehículo cuenta en la cola de su eje |
| `emergency.preemptDistance` | 80 | px | decimal > 0 | Distancia al final de la vía desde la que una ambulancia pide verde (`EmergencyState`) |
| `emergency.maxSeconds` | 30 | s | entero > 0 | Límite de seguridad de `EmergencyState` si nadie lo libera |

## Estadísticas (`stats.*`)

| Clave | Valor | Unidad | Validación y notas | Efecto |
| --- | --- | --- | --- | --- |
| `stats.throughputWindowSeconds` | 60 | s simulados | entero > 0 | Ventana del throughput |
| `stats.sampleSeconds` | 1 | s simulados | entero > 0 | Cada cuánto se agrega un punto a la serie de las gráficas |
| `stats.seriesCapacity` | 300 | puntos | entero > 0 | Puntos que guarda la serie; descarta los más viejos |

## Generación de vehículos (`spawn.*`, `vehicle.maxCount`)

| Clave | Valor | Unidad | Validación y notas | Efecto |
| --- | --- | --- | --- | --- |
| `spawn.intervalMillis` | 6000 | ms simulados | entero > 0. **Default en el código: 300** | Cada cuánto se programa un grupo de vehículos |
| `spawn.batchSize` | 4 | vehículos | entero > 0. **Default en el código: 1** | Vehículos por grupo; se intenta uno por tick |
| `spawn.seed` | 42 | — | entero > 0 | Semilla del spawner (y, combinada, de las estancias en las playas) |
| `spawn.weight.car` | 0.75 | peso | decimal >= 0; al menos un `spawn.weight.*` > 0 | Peso relativo de los autos |
| `spawn.weight.bus` | 0.20 | peso | decimal >= 0 | Peso relativo de los buses |
| `spawn.weight.ambulance` | 0.05 | peso | decimal >= 0 | Peso relativo de las ambulancias |
| `vehicle.maxCount` | 150 | vehículos | entero > 0 | Máximo de vehículos en circulación (los estacionados no cuentan) |

## Vehículos (`vehicle.*`)

| Clave | Valor | Unidad | Validación y notas | Efecto |
| --- | --- | --- | --- | --- |
| `vehicle.car.maxSpeed` | 14 | px/s | decimal > 0 | Velocidad máxima del auto |
| `vehicle.car.acceleration` | 3 | px/s² | decimal > 0 | Aceleración del auto |
| `vehicle.car.deceleration` | 6 | px/s² | decimal > 0 | Frenado máximo del auto |
| `vehicle.car.length` | 12 | px | decimal > 0 | Largo del auto; también define la capacidad aproximada de una vía |
| `vehicle.bus.maxSpeed` | 9 | px/s | decimal > 0 | Velocidad máxima del bus |
| `vehicle.bus.acceleration` | 1.5 | px/s² | decimal > 0 | Aceleración del bus |
| `vehicle.bus.deceleration` | 4 | px/s² | decimal > 0 | Frenado máximo del bus |
| `vehicle.bus.length` | 20 | px | decimal > 0 | Largo del bus |
| `vehicle.ambulance.maxSpeed` | 20 | px/s | decimal > 0 | Velocidad máxima de la ambulancia (limitada por `speedLimit` de la vía) |
| `vehicle.ambulance.acceleration` | 4 | px/s² | decimal > 0 | Aceleración de la ambulancia |
| `vehicle.ambulance.deceleration` | 8 | px/s² | decimal > 0 | Frenado máximo de la ambulancia |
| `vehicle.ambulance.length` | 14 | px | decimal > 0 | Largo de la ambulancia |
| `vehicle.minGap` | 10 | px | decimal > 0 | Distancia mínima al parachoques trasero del de adelante |

## Intersecciones (`intersection.*`)

| Clave | Valor | Unidad | Validación y notas | Efecto |
| --- | --- | --- | --- | --- |
| `intersection.stopLineOffset` | 14 | px | decimal > 0 | Distancia de la línea de detención al centro del nodo; también define la regla del amarillo |
| `intersection.capacity` | 2 | vehículos | entero > 0 | Permisos del `Semaphore` de cada `IntersectionZone` |
| `intersection.boxLength` | 16 | px | decimal > 0 | Largo de la zona de conflicto: al pasarlo, el vehículo libera la zona |
| `intersection.approachMargin` | 2 | px | decimal > 0 | Margen antes de la línea desde el que un vehículo intenta reservar la zona |

## Rutas (`routing.*`)

| Clave | Valor | Unidad | Validación y notas | Efecto |
| --- | --- | --- | --- | --- |
| `routing.congestionFactor` | 2.0 | — | decimal > 0 | Penalización de `LeastCongestedStrategy`: `length × (1 + factor × ocupación)` |
| `routing.randomJitter` | 0.6 | — | decimal > 0 (aunque `RandomRouteStrategy` acepta 0, la clave no) | Variación de `RandomRouteStrategy`: `length × (1 + U[0,1) × jitter)` |
| `routing.weight.shortest` | 0.5 | peso | decimal >= 0; al menos un `routing.weight.*` > 0 | Peso relativo de `SHORTEST` |
| `routing.weight.leastCongested` | 0.3 | peso | decimal >= 0 | Peso relativo de `LEAST_CONGESTED` |
| `routing.weight.random` | 0.2 | peso | decimal >= 0 | Peso relativo de `RANDOM` |

## Capa distribuida (`distributed.*`)

| Clave | Valor | Unidad | Validación y notas | Efecto |
| --- | --- | --- | --- | --- |
| `distributed.latencyMinMillis` | 5 | ms reales | entero >= 0 | Latencia mínima de un mensaje de `SimulatedNetwork` |
| `distributed.latencyMaxMillis` | 40 | ms reales | entero >= 0 y >= `latencyMinMillis` | Latencia máxima (incluida) |
| `distributed.seed` | 7 | — | entero > 0 | Semilla del sorteo de latencias |
| `distributed.mutex` | SEMAPHORE | — | `SEMAPHORE` o `RICART_AGRAWALA` | Modo de reserva de la zona central al arrancar; se cambia en vivo con `SetMutexModeCmd` |
| `distributed.grantTimeoutTicks` | 60 | ticks | entero > 0 | Ticks que un vehículo puede tener la sección de Ricart‑Agrawala sin entrar antes de soltarla |

## Estacionamiento (`parking.*`)

| Clave | Valor | Unidad | Validación y notas | Efecto |
| --- | --- | --- | --- | --- |
| `parking.tripWeight` | 0.35 | probabilidad | decimal entre 0 y 1 | Probabilidad de que un auto nuevo vaya a estacionar |
| `parking.minStaySeconds` | 20 | s simulados | entero >= 0 | Estancia mínima en una playa |
| `parking.maxStaySeconds` | 60 | s simulados | entero >= 0 y >= `minStaySeconds` | Estancia máxima |
| `parking.searchStrategy` | NEAREST_ALTERNATIVE | — | `NEAREST_ALTERNATIVE`, `WAIT_AT_ENTRANCE` o `GIVE_UP` | Qué hace un auto si la playa está llena o cerrada |
| `parking.maxSearchAttempts` | 2 | intentos | entero > 0 | Llegadas fallidas tras las que siempre se rinde |
| `parking.waitAtEntranceSeconds` | 8 | s simulados | entero >= 0 | Espera en la entrada de `WAIT_AT_ENTRANCE` |

## Mapa y log (`map.*`, `log.*`)

| Clave | Valor | Unidad | Validación y notas | Efecto |
| --- | --- | --- | --- | --- |
| `map.file` | maps/city-3x3-v2.json | ruta | texto no vacío | Mapa a cargar, relativo a `src/main/resources/com/astra/` (ver [Mapas](mapas.md)) |
| `log.file` | logs/astra.log | ruta | texto no vacío | Archivo de log; su carpeta se crea si no existe y se sobrescribe en cada ejecución |
| `log.console` | true | — | booleano | Copia cada línea también a la consola |
| `log.level` | INFO | — | `DEBUG`, `INFO`, `WARN` o `ERROR` | Nivel mínimo; las líneas `[LAMPORT]` y `[RICART]` se escriben siempre. Con `DEBUG` se ven las transiciones de los semáforos (categoría `LIGHT`) |

## Comparación con `SimConfig`

Se comparó `config.properties` con las constantes y los getters de `SimConfig`:

- **Claves sin usar:** ninguna. Las 59 claves del archivo tienen su constante en `SimConfig` y se leen.
- **Claves que faltan en el archivo:** ninguna.
- **Defaults distintos del archivo.** El comentario de `SimConfig` dice que los valores por defecto
  "deben coincidir con config.properties", pero hay dos que no:

  | Clave | `config.properties` | Default en `SimConfig` |
  | --- | --- | --- |
  | `spawn.intervalMillis` | 6000 | 300 (`DEFAULT_SPAWN_INTERVAL_MILLIS`) |
  | `spawn.batchSize` | 4 | 1 (`DEFAULT_SPAWN_BATCH_SIZE`) |

  14 clases de test crean la configuración con `SimConfig.from(props)` y fijan solo algunas claves, así
  que el resto toma esos defaults (por ejemplo `SmartVsFixedIntegrationTest` genera un vehículo cada
  300 ms). Igualarlos cambiaría lo que simulan esos tests, así que no se tocaron.
- **Getters sin clave propia o sin uso.** `carMaxSpeed()`, `carAcceleration()` y `carDeceleration()`
  repiten datos de `specFor(VehicleKind.CAR)` y solo los usan los tests. Los demás getters tienen su
  clave y se usan en el código de producción.
