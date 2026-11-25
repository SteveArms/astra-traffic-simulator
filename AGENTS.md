# AGENTS.md — ASTRA Traffic Simulator

Instrucciones para cualquier agente de IA (Claude Code, Codex, Copilot, Cursor, etc.) y para
cualquier persona que contribuya al repositorio. Este archivo es la **fuente de verdad** de las
reglas del proyecto.

---

## 1. Qué es ASTRA

**ASTRA — Asynchronous Simulator of Traffic, Routing & Automata**
(Simulador de Tráfico Urbano).

Simulación 2D interactiva de calles, semáforos inteligentes y vehículos con distintos
comportamientos, desarrollada para el curso de **Tecnologia de Objetos** (UNSA).

- El **motor de simulación** corre en un bucle independiente (hilo propio, *fixed timestep*).
- Los **semáforos** cambian de estado de forma **asíncrona** (`ScheduledExecutorService`).
- La **UI JavaFX** solo dibuja *snapshots* inmutables del mundo.
- Patrones obligatorios: **State** (semáforos), **Strategy** (rutas), **Command** (pausa/eventos).
- Genéricos obligatorios: **`Graph<V, E>`** y **colas de prioridad** (Dijkstra/A*, eventos,
  Ricart‑Agrawala).
- Capa distribuida: **Reloj de Lamport** + **Ricart‑Agrawala** para el cruce de intersecciones.

---

## 2. Stack

| Herramienta | Versión |
|---|---|
| JDK | **21 (LTS)** |
| JavaFX | **21.0.x** (`javafx-controls`) |
| Build | Maven 3.9+ con `javafx-maven-plugin` 0.0.8 |
| JSON | Gson 2.11 (carga de mapas) |
| Tests | JUnit 5 (Jupiter) |

Coordenadas Maven: `groupId=com.astra`, `artifactId=astra-traffic-simulator`.
Clase principal: `com.astra.app.AstraApp` (proyecto modular, `module com.astra`).

---

## 3. Comandos

Ejecutar **siempre desde la raíz** (donde está `pom.xml`):

```bash
mvn clean compile        # compilar
mvn javafx:run           # ejecutar la aplicación
mvn test                 # tests unitarios
mvn clean package        # empaquetar
mvn -Dtest=DijkstraTest test   # un solo test
```

Antes de cualquier commit: `mvn clean test` debe terminar en `BUILD SUCCESS`.

---

## 4. Estructura

```
astra-traffic-simulator/
├── pom.xml
├── AGENTS.md / CLAUDE.md / README.md
├── docs/report/                 # informe LaTeX
└── src/
    ├── main/java/
    │   ├── module-info.java
    │   └── com/astra/
    │       ├── app/          AstraApp, MainController
    │       ├── core/         SimulationEngine, WorldState, WorldSnapshot, PauseGate,
    │       │                 SimConfig, VehicleSpawner, ParallelUpdater
    │       ├── graph/        Graph<V,E>, Edge<V>, Intersection, Road, RoadGraph
    │       │   └── algo/     Dijkstra<V,E>, AStar<V,E>
    │       ├── model/        Vehicle (abstract), Car, Bus, Ambulance, Lane,
    │       │                 VehicleSpec, VehicleSpecs, VehicleFactory
    │       ├── traffic/      TrafficLight, LightState, GreenState, YellowState, RedState,
    │       │                 SmartAdaptiveState, EmergencyState, LightController
    │       ├── routing/      RouteStrategy, ShortestDistanceStrategy,
    │       │                 LeastCongestedStrategy, RandomRouteStrategy,
    │       │                 RouteStrategies, RoadOccupancy, RoutingSettings
    │       ├── command/      Command, CommandBus, PauseCmd, ResumeCmd, StepCmd,
    │       │                 SetSpeedCmd, SpawnVehicleCmd, ForceLightCmd
    │       ├── events/       SimEvent, EventQueue
    │       ├── distributed/  LamportClock, Request, RicartAgrawala
    │       ├── view/         CityRenderer, ControlPanel, StatsPanel
    │       └── util/         MapLoader, SimLogger
    ├── main/resources/com/astra/
    │   ├── maps/city-3x3.json
    │   ├── css/astra.css
    │   └── config.properties
    └── test/java/com/astra/  (espejo de los paquetes)
```

Reglas de dependencia entre paquetes:

- `view` y `app` pueden depender de todo; **nada depende de `view` ni de `app`**.
- `core`, `model`, `traffic`, `routing`, `graph`, `distributed` **no importan `javafx.*`**
  (excepción: ninguna). Esto permite ejecutar y testear el motor sin interfaz gráfica.
- `graph` no conoce `model` ni `traffic`: es genérico.
- `model` solo depende de `graph` y `routing`.

---

## 5. Modelo de concurrencia (REGLAS CRÍTICAS)

| Hilo | Responsabilidad | Puede tocar |
|---|---|---|
| JavaFX Application Thread | UI, `AnimationTimer`, dibujar el snapshot | Nodos JavaFX, `CommandBus.submit()` |
| `astra-engine` | Bucle de simulación (tick fijo, por defecto 50 ms) | `WorldState` (dueño exclusivo) |
| `astra-worker-N` (pool) | Actualizar vehículos en paralelo dentro de un tick | Su vehículo + `Semaphore` de intersección |
| `astra-lights` (`ScheduledExecutorService`) | Transiciones de los semáforos | Estado propio del `TrafficLight` |

Reglas:

1. **Nunca** modificar nodos JavaFX fuera del FX Thread. El motor **no** llama a
   `Platform.runLater`; publica un `WorldSnapshot` en un `AtomicReference` y el
   `AnimationTimer` lo lee.
2. `WorldSnapshot` y todo lo que contiene es **inmutable** (`record`s, `List.copyOf`).
3. La UI **nunca** modifica el mundo directamente: siempre envía un `Command` al `CommandBus`
   (`LinkedBlockingQueue`). El motor drena la cola al **inicio** de cada tick.
4. El estado de un semáforo se expone mediante `volatile` o `AtomicReference<LightState>`.
5. Las zonas de conflicto de cada intersección se protegen con `Semaphore` y **`tryAcquire`**
   (nunca `acquire` bloqueante dentro de un tick → evita congelar el motor y deadlocks).
6. Fin de tick sincronizado con `Phaser` / `invokeAll`; ningún worker sobrevive al tick.
7. La pausa usa `PauseGate` (`ReentrantLock` + `Condition`) y la respetan **motor y
   semáforos**.
8. Todos los hilos son *daemon* o se detienen en `AstraApp.stop()` (`shutdownNow` +
   `awaitTermination`). Nombrar los hilos (`astra-engine`, `astra-lights-1`, …).
9. Colecciones compartidas: `ConcurrentHashMap`, `PriorityBlockingQueue`,
   `CopyOnWriteArrayList`. Prohibido `HashMap`/`ArrayList` compartidos sin sincronizar.
10. Prohibido `Thread.sleep` en el FX Thread y `synchronized` anidados en orden distinto.

---

## 6. Patrones y genéricos (contratos)

```java
// State: un semáforo por intersección que alterna dos ejes (NS y EW).
// Ciclo: GreenState(NS) → YellowState(NS) → RedState(EW) → GreenState(EW) → YellowState(EW)
//        → RedState(NS) → …   (RedState(x) = todo en rojo; después se pone en verde x)
public enum Axis { NS, EW; Axis opposite(); static Axis of(Road road); }
public interface LightTimings { Duration greenSeconds(); Duration yellowSeconds(); Duration allRedSeconds();
                                Duration emergencyMaxSeconds(); }
// Emergencia: TrafficLight.preempt(axis, id) → EmergencyState(axis) (axis verde, el otro rojo) hasta que el
// último vehículo llama a release(id) → YellowState(axis) → ciclo normal.
public interface LightState {
    LightColor colorFor(Axis axis);
    Duration duration(LightTimings timings);   // SimConfig implementa LightTimings
    LightState next();
    default boolean canPass(Axis axis) { return colorFor(axis) == LightColor.GREEN; }
    String name();                              // para logs y HUD, p. ej. "GREEN(NS)"
}

// Strategy
public interface RouteStrategy {
    List<Road> route(Graph<Intersection, Road> graph, Intersection from, Intersection to);
    String name();                              // "SHORTEST", "LEAST_CONGESTED", "RANDOM"
    default boolean isAdaptive() { return false; }   // true = recalcula en cada intersección
}
public interface RoutingSettings { double congestionFactor(); double randomJitter(); }

// Command
public interface Command {
    void execute(SimulationEngine engine);
}

// Genéricos
public class Graph<V, E extends Edge<V>> { ... }
public interface Edge<V> { V from(); V to(); double weight(); }
public class Dijkstra<V, E extends Edge<V>> {
    public List<E> shortestPath(Graph<V, E> g, V src, V dst, ToDoubleFunction<E> weight);
}
```

- Un nuevo comportamiento de semáforo = una nueva clase `LightState`. **No** usar `switch`
  sobre colores en `TrafficLight`: cada estado decide su color por eje.
- `traffic` no depende de `core`: recibe las duraciones a través de `LightTimings`.
- `routing` solo depende de `graph`: recibe sus parámetros a través de `RoutingSettings`
  (`SimConfig` la implementa) y la ocupación de las vías a través de `RoadOccupancy`.
- Una nueva forma de enrutar = una nueva `RouteStrategy`. **No** meter `if` en `Vehicle`.
- Una nueva acción de usuario = un nuevo `Command`.

---

## 7. Capa distribuida

- `LamportClock.tick(process, action)` → incremento atómico; log `[LAMPORT N] PROCESS -> ACTION`.
- `RicartAgrawala` mantiene una `PriorityQueue<Request>` ordenada por `(timestamp, processId)`.
  Log: `[RICART] CAR-42 requested access at 17`, `ENTERED critical section`,
  `released critical section`.
- Cada intersección es un "proceso"; los vehículos de carriles en conflicto solicitan acceso a
  la zona central siguiendo Ricart‑Agrawala.
- El formato de logs debe mantenerse estable: se usa como evidencia en el informe.

---

## 8. Configuración

Ningún número mágico en el código. Todo en `config.properties` leído por `SimConfig`:

```properties
engine.tickMillis=50
engine.parallel=true
engine.workers=4
lights.greenSeconds=8
lights.yellowSeconds=2
lights.allRedSeconds=1
smart.queueThreshold=5
smart.maxExtensionSeconds=6
smart.extensionStepSeconds=2
smart.queueRadius=120
stats.throughputWindowSeconds=60
stats.sampleSeconds=1
stats.seriesCapacity=300
spawn.intervalMillis=6000
spawn.batchSize=4
spawn.seed=42
spawn.weight.car=0.75
spawn.weight.bus=0.20
spawn.weight.ambulance=0.05
vehicle.maxCount=150
vehicle.car.maxSpeed=14
vehicle.car.acceleration=3
vehicle.car.deceleration=6
vehicle.car.length=12
vehicle.bus.maxSpeed=9
vehicle.bus.acceleration=1.5
vehicle.bus.deceleration=4
vehicle.bus.length=20
vehicle.ambulance.maxSpeed=20
vehicle.ambulance.acceleration=4
vehicle.ambulance.deceleration=8
vehicle.ambulance.length=14
vehicle.minGap=10
intersection.stopLineOffset=14
intersection.capacity=2
intersection.boxLength=16
intersection.approachMargin=2
routing.congestionFactor=2.0
routing.randomJitter=0.6
routing.weight.shortest=0.5
routing.weight.leastCongested=0.3
routing.weight.random=0.2
emergency.preemptDistance=80
emergency.maxSeconds=30
distributed.latencyMinMillis=5
distributed.latencyMaxMillis=40
distributed.seed=7
map.file=maps/city-3x3.json
log.file=logs/astra.log
log.console=true
log.level=INFO
```

- `engine.parallel`: con `true`, las fases PLAN y MOVE de cada tick corren en `ParallelUpdater`
  (`astra-worker-N`, sincronizados con `Phaser`); con `false`, en un bucle secuencial en `astra-engine`
  (para depurar y comparar). El COMMIT siempre es secuencial.
- `engine.workers`: hilos `astra-worker-N` (>= 0). Con `0` se usa `availableProcessors() - 1`, mínimo 1.
- `lights.allRedSeconds`: duración de la fase en que **los dos ejes** están en rojo, entre el
  amarillo de un eje y el verde del otro. Deja que los vehículos que ya entraron terminen de
  cruzar. Reemplaza a `lights.redSeconds`: el rojo de un eje ya no tiene duración propia, dura
  lo que dura el verde + amarillo + todo rojo del eje contrario.
- `vehicle.car.deceleration`: frenado máximo de un auto (px/s²).
- `vehicle.<tipo>.{maxSpeed,acceleration,deceleration,length}` (`car`, `bus`, `ambulance`): especificación
  de cada tipo de vehículo (px/s, px/s², px/s², px). `SimConfig.specFor(VehicleKind)` la devuelve como
  `VehicleSpec`; `model` la recibe a través de la interfaz `VehicleSpecs`.
- `vehicle.minGap`: distancia mínima entre el parachoques delantero de un vehículo y el trasero del de
  delante (px).
- `intersection.stopLineOffset`: distancia de la línea de detención al centro de la
  intersección (px).
- `intersection.capacity`: vehículos que pueden estar a la vez dentro de una intersección.
- `intersection.boxLength`: largo de la zona de conflicto que se considera "dentro" (px).
- `intersection.approachMargin`: margen extra antes de la línea de detención a partir del cual un
  vehículo intenta reservar la zona (px); evita reservarla desde lejos.
- `routing.congestionFactor`: penalización de `LeastCongestedStrategy`; el peso de una vía es
  `length * (1 + congestionFactor * ocupación)`, con ocupación entre 0 y 1.
- `routing.randomJitter`: variación máxima de `RandomRouteStrategy`; el peso de una vía es
  `length * (1 + U[0,1) * randomJitter)`, sorteado una vez por cálculo de ruta.
- `emergency.preemptDistance`: distancia al centro de la intersección (px) desde la que un vehículo con
  prioridad de emergencia pone su semáforo en verde (`EmergencyState`) en el eje por donde llega. Lo libera
  al salir del box; sigue necesitando reservar la zona.
- `smart.*` (modo SMART de los semáforos, `SmartAdaptiveState`): la cola de un eje son los vehículos que se
  acercan (parados o no) a `smart.queueRadius` px o menos del centro de la intersección. Si la cola del eje en
  verde es `>= smart.queueThreshold` y la del contrario es menor, el verde se alarga `smart.extensionStepSeconds`
  cada vez, hasta sumar como máximo `smart.maxExtensionSeconds`.
- `stats.*` (`SimStats`): el throughput cuenta las llegadas de los últimos `stats.throughputWindowSeconds`; cada
  `stats.sampleSeconds` de tiempo simulado se agrega un punto a una serie de `stats.seriesCapacity` puntos. La
  espera de un vehículo es el tiempo que pasa por debajo de `Vehicle.WAITING_SPEED` (0.5 px/s).
- `emergency.maxSeconds`: límite de seguridad de `EmergencyState`; si nadie lo libera antes, el semáforo
  sigue solo con el amarillo de ese eje.
- `spawn.*`: `VehicleSpawner` corre dentro del tick (hilo `astra-engine`) y cada `spawn.intervalMillis` de
  tiempo simulado genera un grupo de `spawn.batchSize` vehículos entre intersecciones del borde del mapa, uno
  por tick, mientras haya menos de `vehicle.maxCount`. Si la primera vía no tiene espacio, lo reintenta en el
  tick siguiente con otro origen y destino. `spawn.seed` lo hace reproducible.
- `spawn.weight.{car,bus,ambulance}` y `routing.weight.{shortest,leastCongested,random}`: pesos relativos
  (>= 0, al menos uno > 0 por grupo) de la selección ponderada del tipo de vehículo y de su `RouteStrategy`.
- `distributed.*` (`SimulatedNetwork`): cada mensaje entre procesos tarda un retardo sorteado entre
  `distributed.latencyMinMillis` (>= 0) y `distributed.latencyMaxMillis` (>= el mínimo), ambos en ms e incluidos.
  `distributed.seed` hace reproducible el sorteo.
- `log.*` (`SimLogger`): `log.file` es el archivo de log (su carpeta se crea si no existe y se sobrescribe en cada
  ejecución); con `log.console=true` cada línea se copia también a la consola. `log.level` es el nivel mínimo que se
  escribe (`DEBUG`, `INFO`, `WARN` o `ERROR`); las líneas `[LAMPORT]` y `[RICART]` se escriben siempre.

---

## 9. Estilo de código

- Java 21: usar `record`, `sealed`, `switch` con *pattern matching* y `var` local cuando aclare.
- Identificadores y clases en **inglés**; mensajes de commit, documentación y README en **español**.
- 4 espacios de indentación, llaves estilo K&R, máximo 120 columnas.
- Clases `final` por defecto salvo que se diseñen para herencia.
- Campos `private final` siempre que sea posible.
- Javadoc en interfaces públicas y en todo lo relacionado con concurrencia
  (indicar **qué hilo** llama al método).
- Sin `System.out.println` sueltos: usar `SimLogger` (consola + archivo).

---

## 10. Tests

- JUnit 5, carpeta espejo en `src/test/java`.
- Obligatorios: `GraphTest`, `DijkstraTest`, `AStarTest`, `LightStateTest`,
  `CommandBusTest`, `LamportClockTest` (monotonía con N hilos), `RicartAgrawalaTest`
  (orden por timestamp), `EngineStressTest` (100+ vehículos sin deadlock, con `@Timeout`).
- Los tests del motor corren **sin JavaFX** (headless).

---

## 11. Flujo Git

- Ramas: `main` (estable, solo merges de `release/*`), `develop` (integración).
- Ramas de trabajo desde `develop`: `feature/<tema>`, `fix/<tema>`, `test/<tema>`,
  `docs/<tema>`, `chore/<tema>`.
- Commits con **Conventional Commits**: `type(scope): descripción`
  - `type` y `scope` en **inglés**; la descripción en **español**, en imperativo, sin punto final y de
    máximo 72 caracteres.
  - types: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`, `style`, `perf`
  - scopes: `graph`, `core`, `model`, `traffic`, `routing`, `command`, `events`,
    `distributed`, `view`, `util`, `config`, `report`
  - Ejemplos: `feat(distributed): agrega reloj de Lamport por proceso`,
    `test(core): añade prueba de estrés con 150 vehículos`.
  - El cuerpo (opcional) también va en español y explica el porqué del cambio.
- Un commit = un cambio lógico que compila y pasa los tests.
- Merge a `develop` mediante Pull Request con `--no-ff`; borrar la rama al cerrar.
- Release: `release/v1.0.0` → `main` + tag `v1.0.0` → merge de vuelta a `develop`.

---

## 12. Definition of Done

- [ ] `mvn clean test` en verde.
- [ ] `mvn javafx:run` abre la app y el cambio se ve/funciona.
- [ ] Sin imports de `javafx.*` fuera de `app` y `view`.
- [ ] Sin números mágicos nuevos (van a `config.properties`).
- [ ] Logs `[LAMPORT]` / `[RICART]` intactos si se tocó la capa distribuida.
- [ ] Commit con formato Conventional Commits en la rama correcta.