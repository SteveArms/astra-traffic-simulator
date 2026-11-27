# Arquitectura

ASTRA separa la simulación de la interfaz. El motor corre en su propio hilo y nunca toca JavaFX. La
interfaz solo lee fotos inmutables del mundo (`WorldSnapshot`) y envía acciones como `Command`.

## Visión general

| Capa | Paquetes | Qué hace |
| --- | --- | --- |
| Arranque | `app` | `AstraApp`: carga la configuración y el mapa, arma todos los componentes y maneja el ciclo de vida de los hilos |
| Vista | `view` | `CityRenderer`, `ControlPanel` y `StatsPanel`: dibujan el último `WorldSnapshot` y envían comandos |
| Comandos | `command` | `Command`, `CommandBus` y los comandos concretos |
| Motor | `core` | `SimulationEngine`, `WorldState`, `ParallelUpdater`, zonas, pausa, spawner, playas, estadísticas, `SimConfig` y los `record` del snapshot |
| Dominio | `model`, `traffic`, `routing`, `parking` | Vehículos, semáforos (State), estrategias de ruta (Strategy) y playas de estacionamiento |
| Distribuida | `distributed` | Reloj de Lamport, red simulada, Ricart‑Agrawala e `IntersectionMutex` |
| Base | `graph`, `graph.algo`, `util` | `Graph<V, E>`, Dijkstra, A*, `MapLoader` y `SimLogger` |
| Reservado | `events` | Paquete vacío; solo tiene `package-info.java` |

## Dependencias entre paquetes

El diagrama muestra los `import` reales entre paquetes de `src/main/java`.

```mermaid
flowchart TD
    p_app["app"]
    p_view["view"]
    p_core["core"]
    p_command["command"]
    p_distributed["distributed"]
    p_traffic["traffic"]
    p_graph["graph"]
    p_util["util"]
    p_model["model"]
    p_routing["routing"]
    p_parking["parking"]
    p_algo["graph.algo"]
    p_app --> p_view
    p_app --> p_core
    p_app --> p_command
    p_app --> p_distributed
    p_app --> p_traffic
    p_app --> p_graph
    p_app --> p_util
    p_view --> p_core
    p_view --> p_command
    p_view --> p_model
    p_view --> p_routing
    p_view --> p_traffic
    p_view --> p_graph
    p_view --> p_util
    p_command --> p_core
    p_command --> p_model
    p_command --> p_routing
    p_command --> p_traffic
    p_command --> p_graph
    p_command --> p_util
    p_core --> p_command
    p_core --> p_distributed
    p_core --> p_parking
    p_core --> p_model
    p_core --> p_routing
    p_core --> p_traffic
    p_core --> p_graph
    p_core --> p_util
    p_parking --> p_model
    p_parking --> p_graph
    p_parking --> p_algo
    p_model --> p_routing
    p_model --> p_graph
    p_routing --> p_algo
    p_routing --> p_graph
    p_traffic --> p_graph
    p_traffic --> p_util
    p_distributed --> p_graph
    p_distributed --> p_util
    p_util --> p_graph
    p_algo --> p_graph
    javafx(["javafx.*"])
    p_app -.-> javafx
    p_view -.-> javafx
```

Reglas que cumple el código:

- **Nada de `javafx.*` fuera de `app` y `view`.** `core`, `model`, `traffic`, `routing`, `graph`,
  `parking`, `distributed`, `command` y `util` no importan JavaFX. Por eso los 391 tests corren sin
  interfaz gráfica.
- **Nada depende de `view` ni de `app`.**
- `graph` no conoce `model` ni `traffic`: es genérico.
- `model` solo depende de `graph` y `routing`.
- `parking`, `traffic`, `routing` y `distributed` no dependen de `core`. Reciben sus parámetros por
  interfaces que `SimConfig` implementa: `ParkingSettings`, `LightTimings`, `SmartTimings`,
  `RoutingSettings` y `NetworkSettings`. `model` recibe `VehicleSpecs` y `util` recibe `LogSettings`.
- `core` y `command` se usan entre sí: `SimulationEngine` drena el `CommandBus`, y cada `Command`
  recibe el `SimulationEngine` en `execute`.

`module-info.java` declara el módulo `com.astra`:

```java
module com.astra {
    requires javafx.controls;
    requires com.google.gson;

    exports com.astra.app;

    opens com.astra.util to com.google.gson;
}
```

Solo `com.astra.util` se abre a Gson, porque los DTO del JSON son `record` privados de `MapLoader`.

## Flujo de una acción del usuario

Una acción de la UI nunca modifica el mundo. Viaja como `Command` por el `CommandBus` y el motor la
ejecuta al inicio del siguiente tick. El resultado vuelve a la UI dentro del siguiente
`WorldSnapshot`.

```mermaid
sequenceDiagram
    autonumber
    actor U as Usuario
    participant CP as ControlPanel (FX Thread)
    participant BUS as CommandBus
    participant ENG as SimulationEngine (astra-engine)
    participant W as WorldState
    participant REF as AtomicReference de WorldSnapshot
    participant R as CityRenderer y StatsPanel (FX Thread)

    U->>CP: clic en "Pausar" o Espacio
    CP->>BUS: submit(Commands.togglePause())
    Note over BUS: LinkedBlockingQueue, offer() sin bloquear
    loop cada engine.tickMillis (50 ms)
        ENG->>BUS: drainTo(engine)
        BUS->>ENG: command.execute(engine), en orden FIFO
        alt sin pausa, o con un paso pedido
            ENG->>W: update(dt) en fases A, B y C
        end
        ENG->>W: toSnapshot(tick, simTime, paused, speed)
        W-->>ENG: WorldSnapshot inmutable
        ENG->>REF: set(snapshot)
    end
    R->>REF: get() en cada frame (AnimationTimer) o Timeline
    R-->>U: ciudad, HUD, paneles
```

- La UI llama a `CommandBus.submit` desde el FX Thread; `submit` nunca bloquea.
- `SimulationEngine.tick()` primero ejecuta `commandBus.drainTo(this)`, después actualiza el mundo y al
  final publica el snapshot con `latest.set(...)`.
- El motor no llama a `Platform.runLater`. `CityRenderer` lee el snapshot en su `AnimationTimer`,
  `ControlPanel` cada 100 ms y `StatsPanel` cada 500 ms, los dos con un `Timeline`.
- `WorldSnapshot` y todo lo que contiene son `record` con `List.copyOf` y `Map.copyOf`: el FX Thread
  los lee sin sincronizar.

## Ciclo de vida de `AstraApp`

### `start(Stage)`

`AstraApp.start` corre en el FX Application Thread y arma la aplicación en este orden:

1. `SimConfig.load()` lee `config.properties` y `SimLogger.init(config)` abre el log.
2. `MapLoader.load(config.mapFile())` carga el mapa (por defecto `maps/city-3x3-v2.json`).
3. Crea `SimulationControl` (pausa, velocidad y pasos).
4. Crea `LightController` y lo registra como `PauseListener` de la `PauseGate`.
5. Crea `SimulatedNetwork` y la registra también como `PauseListener`.
6. Crea `WorldState` con las `IntersectionZones` y un `IntersectionMutex` por intersección
   (`IntersectionMutex.forGraph`).
7. Crea el `CommandBus` y el `SimulationEngine` con su `VehicleSpawner`.
8. Crea `CityRenderer`, `ControlPanel` y `StatsPanel`, los conecta entre sí, instala los atajos de
   teclado y muestra la ventana maximizada.
9. Arranca los hilos: `network.start()`, `lights.start()`, `engine.start()` y después los paneles
   (`renderer.start()`, `controlPanel.start()`, `statsPanel.start()`).

### `stop()`

El orden de cierre evita que alguien registre en el log después de cerrarlo:

```mermaid
flowchart LR
    A["statsPanel.stop()"] --> B["controlPanel.stop()"]
    B --> C["renderer.stop()"]
    C --> D["engine.stop()<br/>astra-engine y astra-worker-N"]
    D --> E["lights.stop()<br/>astra-lights-N"]
    E --> F["network.stop()<br/>astra-net"]
    F --> G["SimLogger.shutdown()<br/>astra-logger"]
```

- `SimulationEngine.stop()` hace `shutdownNow()` y `awaitTermination` (1 s) de `astra-engine` y después
  `world.shutdown()`, que detiene el pool de `ParallelUpdater`.
- `LightController.stop()` y `SimulatedNetwork.stop()` también usan `shutdownNow()` y
  `awaitTermination` (1 s y 2 s).
- `SimLogger.shutdown()` vacía la cola y espera al escritor hasta 2 s.
- Todos los hilos son *daemon*, así que la JVM termina aunque alguno no se detenga a tiempo.

## Documentos relacionados

- [Concurrencia](concurrencia.md): hilos, tick de tres fases y prevención de deadlocks.
- [Patrones y genéricos](patrones-y-genericos.md): State, Strategy, Command, `Graph<V, E>`.
- [Capa distribuida](capa-distribuida.md): Lamport y Ricart‑Agrawala.
