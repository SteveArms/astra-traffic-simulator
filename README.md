# ASTRA — Traffic Simulator

**ASTRA** (*Asynchronous Simulator of Traffic, Routing & Automata*) es un simulador 2D de
tráfico urbano con calles, semáforos inteligentes y vehículos con distintos comportamientos.
Lo desarrollamos para el curso de **Tecnología de Objetos** (UNSA).

- El motor de simulación corre en su propio hilo con paso fijo.
- Los semáforos cambian de estado de forma asíncrona (patrón State) y cada intersección limita
  cuántos vehículos cruzan a la vez con un `Semaphore`.
- Dentro de cada tick, los vehículos se actualizan en paralelo en `astra-worker-N`, con sus fases
  sincronizadas por un `Phaser` (ver [Ciclo de un tick](#ciclo-de-un-tick)).
- Autos, buses y ambulancias entran y salen por el borde del mapa, cada uno con su estrategia de
  ruta; las ambulancias ponen en verde el semáforo al que se acercan.
- La interfaz JavaFX solo dibuja *snapshots* inmutables del mundo; cada acción del usuario (pausa,
  paso, velocidad, generar vehículos, forzar un semáforo) es un `Command` que el motor ejecuta al
  inicio del tick.
- Los semáforos tienen un modo inteligente que alarga el verde del eje con más vehículos esperando.
- Los vehículos cruzan las intersecciones con Reloj de Lamport y Ricart‑Agrawala. *(En desarrollo)*

## Requisitos

| Herramienta | Versión |
| --- | --- |
| JDK | 21 o superior (el proyecto compila con `--release 21`) |
| Maven | 3.9 o superior |

JavaFX 21, Gson y JUnit 5 los descarga Maven; no hace falta instalarlos.

## Comandos

Ejecuta todos los comandos desde la raíz del repositorio, donde está `pom.xml`.

```bash
mvn clean compile          # compilar
mvn javafx:run             # ejecutar la aplicación
mvn test                   # ejecutar los tests
mvn -Dtest=DijkstraTest test   # ejecutar un solo test
```

Usa `mvn javafx:run` para abrir la aplicación; `java -jar` falla porque no encuentra JavaFX.

## Ejecutable para Windows

Puedes generar `ASTRA.exe`, que se abre con doble clic, sin consola y sin tener Java ni Maven
instalados: lleva dentro su propio runtime con JavaFX y Gson.

La forma más simple es hacer doble clic en **`build-exe.bat`**, en la raíz del repositorio
(requiere JDK 21+ y Maven en el PATH). Hace lo mismo que estos comandos:

```bat
mvn clean javafx:jlink
jpackage --type app-image --name ASTRA --app-version 0.1.0 ^
  --runtime-image target\astra-runtime ^
  --module com.astra/com.astra.app.AstraApp ^
  --dest target\dist
```

1. `javafx:jlink` crea en `target\astra-runtime` un Java mínimo con ASTRA y sus dependencias.
2. `jpackage` (incluido en el JDK) lo convierte en la carpeta `target\dist\ASTRA\`.
3. Abre `target\dist\ASTRA\ASTRA.exe`.

Para llevarlo a otra PC, copia la carpeta `ASTRA\` completa (unos 90 MB); el `.exe` solo no
funciona. Después de cambiar el código, vuelve a ejecutar `build-exe.bat` con ASTRA cerrado. La
carpeta queda en `target\`, así que Git la ignora.

## Estructura

```
src/main/java/com/astra/
├── app/          AstraApp: punto de entrada JavaFX
├── core/         SimConfig, SimulationEngine, ParallelUpdater, WorldState, WorldSnapshot,
│                 VehicleSpawner, LightView, IntersectionZone(s), PauseGate, SimulationControl,
│                 SimStats, StatsView
├── graph/        Graph<V,E>, Edge, Intersection, Road, RoadGraph, Heuristics
│   └── algo/     Dijkstra, AStar
├── model/        Vehicle, Car, Bus, Ambulance, VehicleSpec(s), VehicleFactory, VehicleKind,
│                 VehicleView; carriles (En desarrollo)
├── traffic/      LightState, Green/Yellow/Red/Emergency/SmartAdaptiveState, TrafficLight,
│                 LightController, LightMode, QueueSensor
├── routing/      RouteStrategy, ShortestDistance/LeastCongested/RandomRouteStrategy,
│                 RouteStrategies, RoadOccupancy
├── command/      Command, CommandBus, Commands y los comandos (Pause, Resume, TogglePause, Step,
│                 SetSpeed, SpawnVehicle, ForceLight, SetLightMode)
├── events/       Eventos y su cola de prioridad (En desarrollo)
├── distributed/  Lamport y Ricart‑Agrawala (En desarrollo)
├── view/         CityRenderer, ControlPanel, StatsPanel
└── util/         MapLoader, MapFormatException; SimLogger (En desarrollo)

src/main/resources/com/astra/
├── config.properties     parámetros de la simulación
├── css/astra.css         tema oscuro de la interfaz
└── maps/city-3x3.json    mapa por defecto
```

Los paquetes `core`, `graph`, `model`, `traffic`, `routing` y `distributed` no dependen de
JavaFX, así que el motor se puede probar sin interfaz gráfica.

## Patrones y genéricos

| Elemento | Dónde | Para qué | Estado |
| --- | --- | --- | --- |
| State | `traffic` (`LightState`, `GreenState`, `YellowState`, `RedState`) | Cada fase del semáforo es una clase que decide el color de cada eje (NS/EW), su duración y la fase siguiente | Implementado |
| Semáforo inteligente | `traffic.SmartAdaptiveState`, `LightMode` | En modo SMART, el verde se alarga de a `smart.extensionStepSeconds` mientras su eje tenga al menos `smart.queueThreshold` vehículos acercándose y el contrario menos, hasta `smart.maxExtensionSeconds` | Implementado |
| Preferencia de paso | `traffic.EmergencyState`, `TrafficLight.preempt/release` | Una ambulancia a menos de `emergency.preemptDistance` pone en verde su eje; al salir del cruce sigue el ciclo normal. Sigue necesitando reservar la zona | Implementado |
| `Semaphore` | `core.IntersectionZone` | Limita cuántos vehículos ocupan a la vez la zona de cruce; solo `tryAcquire`, nunca bloquea el tick | Implementado |
| Strategy | `routing` (`RouteStrategy`) | Cambiar cómo calcula su ruta un vehículo: más corta (Dijkstra), menos congestionada (A* con la ocupación de las vías, recalculada en cada intersección) o aleatoria | Implementado |
| Tipos de vehículo | `model` (`Car`, `Bus`, `Ambulance`, `VehicleFactory`) | Cada tipo tiene su velocidad, aceleración, frenado y largo (`VehicleSpec`); `VehicleSpawner` los genera por pesos | Implementado |
| `Phaser` | `core.ParallelUpdater` | Separa las fases PLAN y MOVE de los `astra-worker-N` dentro de cada tick | Implementado |
| Command | `command` (`Command`, `CommandBus`, `Commands`) | La UI envía acciones a una `LinkedBlockingQueue` y el motor las ejecuta en orden al inicio de cada tick; un comando que falla no detiene a los demás | Implementado |
| Pausa, paso y velocidad | `core` (`PauseGate`, `SimulationControl`) | `PauseGate` (`ReentrantLock` + `Condition`) congela el mundo y los semáforos sin bloquear el motor, que sigue drenando comandos; la velocidad va de ×0.25 a ×4 | Implementado |
| Estadísticas | `core` (`SimStats`, `StatsView`) | Espera y viaje promedio (total y por tipo), throughput de los últimos 60 s, colas y una serie de 300 puntos para las gráficas | Implementado |
| `Graph<V, E extends Edge<V>>` | `graph` | Grafo dirigido genérico y seguro para lecturas concurrentes | Implementado |
| `PriorityQueue` | `graph.algo.Dijkstra` | Cola ordenada por distancia en el cálculo de caminos mínimos | Implementado |
| `PriorityQueue` | `graph.algo.AStar` | Cola ordenada por `f = g + h` con heurístico euclidiano admisible | Implementado |
| `PriorityQueue` | `events`, `distributed` | Cola de eventos y solicitudes de Ricart‑Agrawala ordenadas por `(timestamp, processId)` | En desarrollo |

## Controles

| Control | Acción |
| --- | --- |
| `Espacio` | Pausar o reanudar |
| `S` | Avanzar un tick (solo en pausa) |
| `+` / `−` | Duplicar o dividir la velocidad (entre ×0.25 y ×4) |
| Clic en una intersección | Seleccionarla (también en el panel de semáforos) |
| Clic derecho en una intersección | Forzar verde NS o EW |

Si un botón o una lista tiene el foco, `Espacio` hace su acción propia (por ejemplo, pulsa el
botón); los demás atajos funcionan siempre. Junto a cada semáforo, `↕` y `↔` muestran cuántos
vehículos se acercan por el eje NS y EW.

## Ciclo de un tick

Cada tick (`engine.tickMillis`, 50 ms por defecto) pasa por tres fases. Con `engine.parallel=false`, las
fases A y B corren en un bucle secuencial en `astra-engine`, para depurar y comparar.

```
 astra-engine        astra-worker-1 … astra-worker-N (un bloque de vehículos cada uno)
 ────────────        ─────────────────────────────────────────────────────────────────
     │
     │  envía N tareas, ya registradas en el Phaser
     ├──────────────▶ ┌─ A · PLAN ────────────────────────────────────────────────┐
     │                │ distancia libre de cada vehículo → plan[i]                │
     │                │ solo lee offsets ajenos (nadie se mueve hasta B)          │
     │                │ zone.tryEnter · preempt · entryClaims.putIfAbsent         │
     │                └───────────────────────────────────────────────────────────┘
     │ espera (timeout 2 × tick)        ═══ Phaser: todos los PLAN terminan ═══
     │                ┌─ B · MOVE ────────────────────────────────────────────────┐
     │                │ vehicle.update(dt, plan[i]): solo su propio estado        │
     │                └───────────────────────────────────────────────────────────┘
     │ espera (timeout 2 × tick)        ═══ Phaser: todos los MOVE terminan ═══
     ▼
 ┌─ C · COMMIT (secuencial) ───────────────────────────────────────────────────┐
 │ libera zonas y semáforos · rutas adaptativas · elimina a los que llegaron   │
 │ VehicleSpawner · índice por vía · RoadOccupancy · publica WorldSnapshot     │
 └─────────────────────────────────────────────────────────────────────────────┘
```

Si una tarea falla, sigue llegando al `Phaser` para no dejarlo colgado y el motor relanza la
excepción al terminar la fase. En paralelo, los workers compiten de verdad por las zonas, así que
dos ejecuciones con la misma semilla pueden diferir en el orden de llegada; en modo secuencial son
idénticas.

## Formato del mapa

Los mapas son archivos JSON en `src/main/resources/com/astra/maps/`. El mapa que se carga se
indica en `config.properties` con la clave `map.file`.

```json
{
  "name": "Mini",
  "intersections": [
    { "id": "A", "x": 150, "y": 100 },
    { "id": "B", "x": 350, "y": 100 }
  ],
  "roads": [
    { "id": "R1", "from": "A", "to": "B", "speedLimit": 14, "lanes": 1, "twoWay": true }
  ]
}
```

| Campo de `roads` | Obligatorio | Descripción |
| --- | --- | --- |
| `id`, `from`, `to` | Sí | `from` y `to` son ids de intersecciones existentes |
| `speedLimit` | Sí | Velocidad máxima, mayor que 0 |
| `lanes` | Sí | Número de carriles, al menos 1 |
| `twoWay` | No | Si es `true` crea dos vías: `<id>-F` y `<id>-B`. Si falta, la vía es de un solo sentido |
| `length` | No | Si falta, se usa la distancia euclidiana entre las intersecciones |

Si el mapa tiene ids duplicados, referencias a intersecciones inexistentes o listas vacías, la
carga falla con `MapFormatException` y un mensaje que indica el problema.

## Flujo Git

- `main`: versión estable; solo recibe merges de `release/*`.
- `develop`: rama de integración.
- `feature/<tema>`, `fix/<tema>`, `test/<tema>`, `docs/<tema>`, `chore/<tema>`: se crean desde
  `develop` y vuelven a ella mediante Pull Request.
- Commits con [Conventional Commits](https://www.conventionalcommits.org/), por ejemplo
  `feat(graph): add Dijkstra shortest path`.

Las reglas completas del proyecto están en [AGENTS.md](AGENTS.md) y las indicaciones para
Claude Code en [CLAUDE.md](CLAUDE.md).

## Integrantes

| Nombre | Rol |
| --- | --- |
| *Por definir* | *Por definir* |
| *Por definir* | *Por definir* |
