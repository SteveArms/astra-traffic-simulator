# Pruebas

Los tests usan JUnit 5 (Jupiter 5.11.3) y están en `src/test/java`, en carpetas espejo de los paquetes.
Todos corren **sin JavaFX en pantalla**: el motor se prueba headless, normalmente con
`SimulationEngine.step()`, que ejecuta cada tick en el hilo `astra-engine` y espera a que termine.

## Cómo correrlos

Desde la raíz del repositorio, donde está `pom.xml`:

```bash
mvn test                                  # todos los tests
mvn clean test                            # compila desde cero y corre todos
mvn -Dtest=DijkstraTest test              # una clase
mvn -Dtest=RicartAgrawalaTest#singleEntryCostsTwoNMinusOneMessages test   # un método
```

Surefire corre con `useModulePath=false`, así que los tests ven las clases del paquete aunque el
proyecto sea modular. Los informes quedan en `target/surefire-reports/`.

## Resultado de la verificación

Última ejecución de `mvn clean test` sobre la rama `docs/full-documentation`, ya con los commits de
`feature/parked-cars` (JDK 26.0.1 con `--release 21`, Maven 3.9.9):

```text
Tests run: 391, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Total time: 56.979 s
```

Antes de esos commits, la misma suite tenía 387 tests en 49 clases, también sin fallos. Ahora son 50
clases de test. `ManualScheduler` y `TestLights` (en `traffic`) son ayudantes, no tests.

| Paquete | Clases | Tests |
| --- | --- | --- |
| `command` | 2 | 21 |
| `core` | 16 | 160 |
| `distributed` | 6 | 34 |
| `graph` | 3 | 31 |
| `graph.algo` | 2 | 15 |
| `model` | 3 | 25 |
| `parking` | 3 | 18 |
| `routing` | 5 | 17 |
| `traffic` | 6 | 40 |
| `util` | 2 | 23 |
| `view` | 2 | 7 |
| **Total** | **50** | **391** |

## Qué verifica cada clase

### `command`

| Clase | Tests | Verifica |
| --- | --- | --- |
| `CommandBusTest` | 6 | Orden FIFO, que un comando que falla no detiene a los demás, el historial de 50, varios productores con un solo consumidor y que el comando corre en `astra-engine` al inicio del tick |
| `CommandsTest` | 15 | Cada comando de punta a punta: pausa, reanudar y alternar, pasos solo en pausa, velocidad, generar vehículos (con extremos explícitos, al azar o inválidos), forzar luz, modo de semáforo y modo de exclusión mutua, y que todos corren en `astra-engine` |

### `core`

| Clase | Tests | Verifica |
| --- | --- | --- |
| `DistributedCrossingTest` | 3 | Ricart‑Agrawala de punta a punta con el hilo `astra-net` real: un solo acceso por box, sin deadlock, cambio de modo en vivo sin vehículos trabados y que se escriben las líneas `[LAMPORT]` y `[RICART]` |
| `DrivingTest` | 12 | Conducción en un cruce en +: se detiene en rojo, cruza en verde y libera la zona, mantiene `minGap`, la ambulancia cruza sin detenerse, rutas adaptativas, zona llena, última vía y ocupación |
| `EnginePauseTest` | 5 | La pausa congela el mundo pero los ticks siguen, los pasos pedidos avanzan exactamente esos ticks, reanudar mueve de nuevo y la velocidad duplica la distancia por tick y se limita a su rango |
| `EngineStressTest` | 2 | Carga de 150 vehículos con el motor en paralelo (ver abajo) |
| `IntersectionZoneTest` | 5 | El tercer vehículo espera, entrar dos veces no toma otro permiso, salir dos veces no pasa la capacidad, contención con varios hilos y `releaseAll` |
| `LightPauseTest` | 3 | Un semáforo en pausa conserva el tiempo que le falta, `preempt` en pausa no programa nada y la velocidad ×2 acorta a la mitad la fase siguiente |
| `ParallelUpdaterTest` | 8 | Cada índice se planifica y se mueve una vez, todos los PLAN terminan antes de cualquier MOVE, hilos con nombre, fallos en PLAN y MOVE llegan al motor sin colgarse, timeout con el nombre de la fase y `shutdown` |
| `ParkingIntegrationTest` | 4 | Viajes con estacionamiento en el mapa v2 con el motor en paralelo, y que el `ParkingView` del snapshot muestre en cada cajón al mismo auto que ocupa la playa (ver abajo) |
| `PauseGateTest` | 5 | `awaitResumed` con reanudación, timeout y sin pausa; los listeners se llaman una vez por cambio y fuera del lock |
| `QueueSensorTest` | 2 | Las colas se cuentan por eje y solo dentro de `smart.queueRadius` |
| `SimConfigTest` | 79 | Lectura del archivo real, defaults, cada tipo de validación (con tests parametrizados por clave), pesos, `engine.workers=0`, latencias, modo de exclusión, log y estacionamiento |
| `SimStatsTest` | 8 | Promedios exactos, ventana de 60 s del throughput, escala por minuto, serie acotada y un punto por segundo simulado, cola actual y máxima, y coherencia con el spawner en una corrida headless |
| `SimulationEngineTest` | 5 | `step()` avanza y mueve, `start/stop` crean y matan el hilo, arrancar dos veces o hacer `step` en marcha falla, y los vehículos que llegan se eliminan y cuentan |
| `SmartVsFixedIntegrationTest` | 2 | SMART no espera más que FIXED + 10 % (semilla 42) y espera menos en promedio sobre cinco semillas |
| `VehicleSpawnerTest` | 10 | Misma semilla da los mismos conteos, nunca pasa `vehicle.maxCount`, un grupo por intervalo, proporciones por tipo, contadores coherentes y extremos distintos del borde o los gateways |
| `WorldSnapshotTest` | 7 | El snapshot es inmutable y copia sus listas y mapas; rechaza nulos; `LightView` informa el estado de la zona |

### `distributed`

| Clase | Tests | Verifica |
| --- | --- | --- |
| `IntersectionMutexTest` | 7 | Orden FIFO dentro de un acceso, solo el primero tiene la sección, cancelar al que la tiene la pasa a otro acceso, cancelar a uno que espera, cancelar antes de la concesión y un acceso solo entra sin mensajes |
| `LamportClockTest` | 8 | Empieza en 0, `tick` incrementa, valores distintos y consecutivos con varios hilos, las tres variantes de `receive`, *happens-before* entre envío y recepción y el formato `[LAMPORT N] PROCESS -> ACTION` |
| `MessageTest` | 3 | Campos, nulos y timestamp negativo |
| `RequestTest` | 5 | Orden por timestamp y desempate por `processId`, una `PriorityQueue<Request>` respeta el orden, `compareTo` coherente con `equals` |
| `RicartAgrawalaTest` | 5 | Cuatro nodos con 200 entradas cada uno nunca se superponen, todos terminan y pagan 2(N − 1) mensajes por entrada; pedidos concurrentes entran por `(timestamp, processId)`; un nodo solo entra sin mensajes |
| `SimulatedNetworkTest` | 6 | Cada mensaje se entrega una vez en `astra-net`, latencia dentro del rango, la red no es FIFO, la pausa retiene y la reanudación entrega, destino desconocido y `stop` idempotente |

### `graph` y `graph.algo`

| Clase | Tests | Verifica |
| --- | --- | --- |
| `GraphTest` | 8 | Grafo vacío, vértices idempotentes, aristas dirigidas, extremos desconocidos, colecciones no modificables y nulos |
| `IntersectionTest` | 5 | Tipos de nodo y reglas de `capacity` |
| `RoadGraphTest` | 18 | Vías de doble sentido (`-F`/`-B`), largo euclidiano, ids duplicados y validaciones de `Road` (parametrizadas) |
| `AStarTest` | 8 | El heurístico euclidiano no expande más nodos que `h = 0`, peso propio, sin camino, mismo origen y destino, vértice desconocido y peso negativo |
| `DijkstraTest` | 7 | Camino óptimo conocido, sin camino, mismo origen y destino, errores, peso propio y ruta por distancia o por tiempo |

### `model`

| Clase | Tests | Verifica |
| --- | --- | --- |
| `VehicleFactoryTest` | 5 | Prefijo de id y clase por tipo, especificación de cada tipo, ruta inicial y que solo la ambulancia tiene prioridad |
| `VehicleSpecTest` | 2 | Valores positivos y rechazo de no positivos o no finitos |
| `VehicleTest` | 18 | Cambio de vía, tiempos de viaje y espera, llegada, tope de velocidad, rumbo, rutas inválidas, `reroute`, viajes THROUGH y PARK, y que solo los autos estacionan |

### `parking`

| Clase | Tests | Verifica |
| --- | --- | --- |
| `ParkingLotTest` | 4 | Estaciona hasta llenarse y cuenta entradas, rechazos y salidas; cerrada rechaza pero deja salir; solo nodos `PARKING` |
| `ParkingSearchStrategyTest` | 8 | Las tres estrategias, el tope de `ParkingSearchPolicy` y su creación desde la configuración |
| `ResourcePoolTest` | 6 | 20 hilos nunca superan la capacidad, lleno rechaza sin bloquear, `release` idempotente, un ítem conserva su lugar y los slots muestran quién ocupa cada uno |

### `routing`

| Clase | Tests | Verifica |
| --- | --- | --- |
| `LeastCongestedStrategyTest` | 4 | Evita las vías congestionadas, sin ocupación coincide con la más corta, es adaptativa y rechaza un factor negativo |
| `RandomRouteStrategyTest` | 4 | Misma semilla da la misma ruta, semillas distintas dan más de una, rutas conexas y jitter negativo |
| `RoadOccupancyTest` | 3 | Vía desconocida libre, copia del mapa y rango [0, 1] |
| `RouteStrategiesTest` | 3 | Crea cada estrategia por nombre, nombres canónicos y nombre desconocido |
| `ShortestDistanceStrategyTest` | 3 | Mismo nodo da ruta vacía, nombre y no adaptativa |

### `traffic`

| Clase | Tests | Verifica |
| --- | --- | --- |
| `AxisTest` | 4 | Vías horizontales son EW, verticales NS y `opposite` |
| `EmergencyPreemptionTest` | 5 | `preempt` pone verde rápido y `release` retoma el ciclo, sobre un eje ya en verde, espera a todas las ambulancias, límite de seguridad y `preempt`/`release` concurrentes |
| `LightStateTest` | 6 | Doce transiciones siguen el ciclo exacto, nunca dos ejes fuera de rojo a la vez, duraciones, `canPass`, nombres y `EmergencyState` |
| `SmartAdaptiveStateTest` | 11 | Colores, duraciones, extensión hasta el máximo, sin extensión si el otro eje está cargado o la cola es corta |
| `TrafficLightSmartTest` | 4 | Con el scheduler real, el verde SMART dura más con cola alta, cada verde nuevo es SMART, volver a FIXED y nunca dos ejes en verde |
| `TrafficLightTest` | 10 | Los semáforos avanzan solos, nunca dos ejes en verde, `stop` mata los hilos, damero, `canEnter` y dónde hay semáforo (no en esquinas en L, ejes únicos ni `JUNCTION`) |

### `util` y `view`

| Clase | Tests | Verifica |
| --- | --- | --- |
| `MapLoaderTest` | 18 | El mapa real y el viejo cargan, `kind`, `capacity`, `bounds`, `length` y todas las validaciones de [Mapas](mapas.md#validaciones-de-maploader) |
| `SimLoggerTest` | 5 | Formato exacto por categoría, filtro por nivel, líneas completas con escritores concurrentes, `shutdown` vacía la cola y crea la carpeta del log |
| `LotGeometryTest` | 4 | Un cajón por plaza repartido en dos filas, el orden de los cajones desde la entrada igual al del `ResourcePool`, cada cajón dentro de la playa y mirando al pasillo, y el tamaño de la playa según su capacidad |
| `ViewTransformTest` | 3 | Encaja el mundo con margen y centrado, la inversa deshace la transformación y un canvas degenerado usa la identidad |

## Tests de concurrencia y estrés

Todos llevan `@Timeout` para que un deadlock haga fallar el test en lugar de colgar el build.

| Test | Escenario | Invariantes |
| --- | --- | --- |
| `EngineStressTest.parallelEngineKeepsInvariantsUnderLoad` | Malla de 6 × 4 cruces, 150 vehículos como máximo, un grupo cada 100 ms, 3000 ticks con el motor en paralelo; los semáforos avanzan a mano cada 160 ticks. `@Timeout(120)` | En cada tick: ninguna zona supera su capacidad, nadie queda a menos de `minGap` del de adelante, nadie se pasa un rojo sin tener la zona y nunca hay más de 150 vehículos. Al final se llegó a 150 y salió al menos uno |
| `EngineStressTest.parallelThroughputMatchesSequentialWithin15Percent` | La misma carga en paralelo y en secuencial | Los vehículos que salen difieren menos de un 15 % |
| `DistributedCrossingTest.ricartAgrawalaKeepsOneAccessPerBoxWithoutDeadlock` | La malla de 6 × 4 con 150 vehículos, 3000 ticks, en modo `RICART_AGRAWALA` y con `astra-net` real | Quien cruza tiene la sección; como máximo un acceso por intersección cruza con ella; hubo mensajes y siguió habiendo llegadas en el último tercio (sin deadlock) |
| `DistributedCrossingTest.switchingModesLiveLeavesNoVehicleStuck` | Cambiar a `SEMAPHORE` con vehículos esperando en Ricart‑Agrawala | No queda nadie encolado ni con la sección, y los que esperaban terminan de cruzar |
| `ParkingIntegrationTest` | Mapa v2 con el motor en paralelo, 5000 ticks | Ninguna playa supera su capacidad, `entradas − salidas = ocupados`, el snapshot coincide con los cajones, nadie está estacionado y circulando a la vez, solo autos con salida por un gateway, rechazos contados y misma semilla con el mismo resultado en secuencial |
| `RicartAgrawalaTest.fourNodesNeverOverlapAllFinishAndPayTwoNMinusOneMessages` | 4 nodos, 200 entradas cada uno, red real | Nunca hay más de un nodo en `HELD`, nadie muere de hambre, todos terminan en `RELEASED` y se enviaron exactamente `4 × 200 × 2 × 3` mensajes |
| `LamportClockTest.concurrentTicksAreDistinctAndConsecutive` | 16 hilos con 1000 `tick` cada uno sobre el mismo reloj | Los valores son distintos y consecutivos (monotonía) |
| `IntersectionZoneTest.occupancyNeverExceedsCapacityUnderContention`, `ResourcePoolTest.twentyThreadsNeverExceedCapacity` | Muchos hilos compitiendo con `tryAcquire` | La ocupación nunca supera la capacidad |
| `CommandBusTest.concurrentProducersAndSingleConsumer` | Varios productores y un único consumidor que drena la cola | Se ejecutan exactamente todos los comandos enviados: no se pierde ni se repite ninguno |
| `SimLoggerTest.concurrentWritersProduceWholeLines` | Varios hilos escribiendo en el log | Cada línea sale completa |

El mapa 3 × 3 no admite 150 vehículos a la vez, por eso los tests de estrés usan una malla de 6 × 4.
