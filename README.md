# ASTRA — Traffic Simulator

**ASTRA** (*Asynchronous Simulator of Traffic, Routing & Automata*) es un simulador 2D de tráfico
urbano: una ciudad vista desde arriba con semáforos inteligentes, playas de estacionamiento y autos,
buses y ambulancias que eligen su ruta de distintas formas. Lo desarrollamos para el curso de
**Tecnología de Objetos** (UNSA).

`Java 21` · `JavaFX 21.0.5` · `Maven 3.9+` · `Gson 2.11` · `JUnit 5.11`

<!-- Captura pendiente: agregar docs/img/astra-main.png y reemplazar este comentario por
![ASTRA en ejecución](docs/img/astra-main.png) -->
*Captura pendiente: `docs/img/astra-main.png`.*

## Características

- **Motor de paso fijo** en su propio hilo (`astra-engine`), con un tick de 50 ms dividido en tres fases;
  las dos primeras corren en paralelo en `astra-worker-N`, sincronizadas con un `Phaser`.
- **Semáforos asíncronos** con el patrón State en un `ScheduledExecutorService`: ciclo fijo o modo
  inteligente, que alarga el verde del eje con más demanda.
- **Preferencia de paso:** las ambulancias ponen en verde el semáforo al que se acercan.
- **Tres estrategias de ruta** (Strategy): la más corta (Dijkstra), la menos congestionada (A*,
  recalculada en cada cruce) y una aleatoria.
- **Exclusión mutua en los cruces** con un `Semaphore` de capacidad o, en vivo, con **Ricart‑Agrawala**
  por paso de mensajes y relojes de **Lamport**.
- **Playas de estacionamiento** con cajones como recurso compartido (`ResourcePool<T>`) y tres
  estrategias para cuando están llenas.
- **Interfaz JavaFX** que solo dibuja *snapshots* inmutables; cada acción del usuario es un `Command`.
- **Estadísticas en vivo:** espera y viaje promedio, throughput, colas y dos gráficas.
- **Logs de evidencia** `[LAMPORT N] PROCESS -> ACTION` y `[RICART] ...` en `logs/astra.log`.
- **387 tests** JUnit 5 que corren sin interfaz gráfica, incluidas pruebas de estrés con 150 vehículos.

## Requisitos

| Herramienta | Versión |
| --- | --- |
| JDK | 21 o superior (el proyecto compila con `--release 21`) |
| Maven | 3.9 o superior |

JavaFX, Gson y JUnit los descarga Maven; no hace falta instalarlos.

## Instalación y ejecución

```bash
git clone <url-del-repositorio>
cd astra-traffic-simulator
```

Ejecuta todos los comandos desde la raíz del repositorio, donde está `pom.xml`.

```bash
mvn clean compile                # compilar
mvn javafx:run                   # ejecutar la aplicación
mvn test                         # ejecutar los 387 tests
mvn -Dtest=DijkstraTest test     # ejecutar una sola clase de test
mvn javadoc:javadoc              # generar el Javadoc en target/reports/apidocs/
```

Usa `mvn javafx:run` para abrir la aplicación; `java -jar` falla porque no encuentra JavaFX. Los
parámetros de la simulación están en `src/main/resources/com/astra/config.properties` (ver
[Configuración](docs/configuracion.md)).

**Pendiente:** un runner de escenarios sin interfaz (por ejemplo `ScenarioRunner`) para generar trazas y
métricas reproducibles. No existe en el código.

### Ejecutable para Windows

`build-exe.bat`, en la raíz, genera `target\dist\ASTRA\ASTRA.exe`, que se abre con doble clic, sin
consola y sin tener Java instalado: lleva su propio runtime con JavaFX y Gson. Requiere JDK 21+ y Maven en
el `PATH`. Hace lo mismo que:

```bat
mvn clean javafx:jlink
jpackage --type app-image --name ASTRA --app-version 0.1.0 ^
  --runtime-image target\astra-runtime ^
  --module com.astra/com.astra.app.AstraApp ^
  --dest target\dist
```

1. `javafx:jlink` crea en `target\astra-runtime` un Java mínimo con ASTRA y sus dependencias.
2. `jpackage` (incluido en el JDK) lo convierte en la carpeta `target\dist\ASTRA\`.

Para llevarlo a otra PC, copia la carpeta `ASTRA\` completa; el `.exe` solo no funciona. Después de
cambiar el código, vuelve a ejecutar `build-exe.bat` con ASTRA cerrado. La carpeta queda en `target\`,
así que Git la ignora.

## Controles

| Control | Acción |
| --- | --- |
| `Espacio` | Pausar o reanudar |
| `S` | Avanzar un tick (solo en pausa) |
| `+` / `-` (o `−`) | Duplicar o dividir la velocidad, entre ×0.25 y ×4 |
| Clic en un cruce con semáforo | Seleccionarlo (también en la lista del panel **Semáforos**) |
| Clic derecho en un cruce con semáforo | Forzar verde NS o EW |
| Botones del panel izquierdo | Pausar, Paso, Velocidad, Mostrar etiquetas, Generar, Generar ×10, Fijo / Inteligente, Verde NS / EW, Semaphore / Ricart-Agrawala |

Si un botón o una lista tiene el foco, `Espacio` hace su acción propia (por ejemplo, pulsa el botón); los
demás atajos funcionan siempre. El recorrido completo de la interfaz está en la
[Guía de usuario](docs/guia-de-usuario.md).

## Requisitos del curso

| Requisito | Dónde se cumple |
| --- | --- |
| Hilos y concurrencia | `SimulationEngine` (`astra-engine`), `ParallelUpdater` (`astra-worker-N` con `Phaser`), `LightController` (`astra-lights-N`), `SimulatedNetwork` (`astra-net`), `SimLogger` (`astra-logger`); `IntersectionZone` con `Semaphore` y `tryAcquire`; `PauseGate` con `ReentrantLock` y `Condition` |
| Patrón State | `LightState` con `GreenState`, `YellowState`, `RedState`, `EmergencyState` y `SmartAdaptiveState`; lo usa `TrafficLight` |
| Patrón Strategy | `RouteStrategy` con `ShortestDistanceStrategy`, `LeastCongestedStrategy` y `RandomRouteStrategy`; `ParkingSearchStrategy` con `NearestAlternativeStrategy`, `WaitAtEntranceStrategy` y `GiveUpStrategy` |
| Patrón Command | `Command`, `CommandBus` (`LinkedBlockingQueue`) y `PauseCmd`, `ResumeCmd`, `TogglePauseCmd`, `StepCmd`, `SetSpeedCmd`, `SpawnVehicleCmd`, `ForceLightCmd`, `SetLightModeCmd`, `SetMutexModeCmd` |
| Genéricos | `Graph<V, E extends Edge<V>>` (y `RoadGraph`), `Dijkstra<V, E>`, `AStar<V, E>`, `ResourcePool<T>` |
| Colas de prioridad | `PriorityQueue` en `Dijkstra` (por distancia) y en `AStar` (por `f = g + h`); `Request` define el orden `(timestamp, processId)` de Ricart‑Agrawala |
| Reloj de Lamport | `LamportClock`: un reloj por proceso con `tick`, `send` y `receive` |
| Ricart‑Agrawala | `RicartAgrawalaNode` por paso de mensajes sobre `SimulatedNetwork`; `IntersectionMutex` lo usa en cada cruce |

## Documentación

| Documento | Contenido |
| --- | --- |
| [Arquitectura](docs/arquitectura.md) | Paquetes y dependencias, flujo UI → comando → motor → snapshot y ciclo de vida |
| [Concurrencia](docs/concurrencia.md) | Hilos, tick de tres fases, pausa, zonas, playas y prevención de deadlocks |
| [Patrones y genéricos](docs/patrones-y-genericos.md) | State, Strategy, Command, `Graph<V, E>`, Dijkstra, A* y colas de prioridad |
| [Capa distribuida](docs/capa-distribuida.md) | Lamport, red simulada, Ricart‑Agrawala, `IntersectionMutex` y una traza real |
| [Simulación](docs/simulacion.md) | Modelo de conducción, vehículos, spawner, ambulancias, SMART, playas y métricas |
| [Mapas](docs/mapas.md) | Formato JSON, validaciones y el mapa `city-3x3-v2.json` |
| [Configuración](docs/configuracion.md) | Todas las claves de `config.properties` |
| [Pruebas](docs/pruebas.md) | Cómo correr los tests y qué verifica cada clase |
| [Guía de usuario](docs/guia-de-usuario.md) | Paneles, símbolos del mapa y tres recorridos guiados |
| [Cómo contribuir](docs/contribuir.md) | Flujo Git, formato de commits y Definition of Done |
| [CHANGELOG](CHANGELOG.md) | Cambios por día y por rama |

Las reglas del proyecto para personas y agentes de IA están en [AGENTS.md](AGENTS.md), y las
indicaciones para Claude Code en [CLAUDE.md](CLAUDE.md).

## Estructura

```text
astra-traffic-simulator/
├── pom.xml, build-exe.bat
├── README.md, CHANGELOG.md, AGENTS.md, CLAUDE.md
├── docs/                         documentación
└── src/
    ├── main/java/com/astra/
    │   ├── app/          AstraApp
    │   ├── core/         motor, estado del mundo, zonas, pausa, spawner, playas, estadísticas, SimConfig
    │   ├── graph/        Graph<V,E>, Edge, Intersection, NodeKind, Road, RoadGraph, Heuristics
    │   │   └── algo/     Dijkstra, AStar
    │   ├── model/        Vehicle, Car, Bus, Ambulance, VehicleSpec, VehicleFactory
    │   ├── traffic/      LightState y sus estados, TrafficLight, LightController
    │   ├── routing/      RouteStrategy y sus estrategias, RoadOccupancy
    │   ├── parking/      ResourcePool<T>, ParkingLot, ParkingDirectory y estrategias de búsqueda
    │   ├── command/      Command, CommandBus, Commands y los comandos
    │   ├── distributed/  LamportClock, SimulatedNetwork, RicartAgrawalaNode, IntersectionMutex
    │   ├── events/       reservado (vacío)
    │   ├── view/         CityRenderer, ControlPanel, StatsPanel y los painters
    │   └── util/         MapLoader, SimLogger
    ├── main/resources/com/astra/
    │   ├── config.properties
    │   ├── css/astra.css
    │   └── maps/         city-3x3-v2.json (por defecto), city-3x3.json (tests)
    └── test/java/com/astra/      49 clases de test, en espejo de los paquetes
```

## Pendiente

- Captura de pantalla en `docs/img/astra-main.png`.
- Runner de escenarios sin interfaz (`ScenarioRunner`) y la carpeta `docs/report/evidence/`.
- Informe LaTeX en `docs/report/`.
- Cerrar una playa desde la interfaz y mostrar las métricas de estacionamiento.
- Paquete `events` (cola de eventos con prioridad): reservado y vacío.

## Equipo

| Nombre | Rol |
| --- | --- |
| *Por definir* | *Por definir* |
| *Por definir* | *Por definir* |

## Créditos

Proyecto del curso **Tecnología de Objetos**, Universidad Nacional de San Agustín de Arequipa (UNSA).
Ricart‑Agrawala sigue el algoritmo de G. Ricart y A. K. Agrawala, *An Optimal Algorithm for Mutual
Exclusion in Computer Networks* (1981), y los relojes lógicos siguen a L. Lamport, *Time, Clocks, and
the Ordering of Events in a Distributed System* (1978).
