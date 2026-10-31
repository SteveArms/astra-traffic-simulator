# ASTRA — Traffic Simulator

**ASTRA** (*Asynchronous Simulator of Traffic, Routing & Automata*) es un simulador 2D de
tráfico urbano con calles, semáforos inteligentes y vehículos con distintos comportamientos.
Lo desarrollamos para el curso de **Tecnología de Objetos** (UNSA).

- El motor de simulación corre en su propio hilo con paso fijo.
- Los semáforos cambian de estado de forma asíncrona (patrón State) y cada intersección limita
  cuántos vehículos cruzan a la vez con un `Semaphore`.
- La interfaz JavaFX solo dibuja *snapshots* inmutables del mundo.
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

## Estructura

```
src/main/java/com/astra/
├── app/          AstraApp: punto de entrada JavaFX
├── core/         SimConfig, SimulationEngine, WorldState, WorldSnapshot, LightView,
│                 IntersectionZone(s), DemoScenario
├── graph/        Graph<V,E>, Edge, Intersection, Road, RoadGraph
│   └── algo/     Dijkstra; A* (En desarrollo)
├── model/        Vehicle, Car, VehicleKind, VehicleView; Bus, Ambulance y carriles (En desarrollo)
├── traffic/      LightState, Green/Yellow/RedState, TrafficLight, LightController;
│                 SmartAdaptiveState (En desarrollo)
├── routing/      Estrategias de ruta (En desarrollo)
├── command/      Comandos y CommandBus (En desarrollo)
├── events/       Eventos y su cola de prioridad (En desarrollo)
├── distributed/  Lamport y Ricart‑Agrawala (En desarrollo)
├── view/         CityRenderer; ControlPanel y StatsPanel (En desarrollo)
└── util/         MapLoader, MapFormatException; SimLogger (En desarrollo)

src/main/resources/com/astra/
├── config.properties     parámetros de la simulación
└── maps/city-3x3.json    mapa por defecto
```

Los paquetes `core`, `graph`, `model`, `traffic`, `routing` y `distributed` no dependen de
JavaFX, así que el motor se puede probar sin interfaz gráfica.

## Patrones y genéricos

| Elemento | Dónde | Para qué | Estado |
| --- | --- | --- | --- |
| State | `traffic` (`LightState`, `GreenState`, `YellowState`, `RedState`) | Cada fase del semáforo es una clase que decide el color de cada eje (NS/EW), su duración y la fase siguiente | Implementado (`SmartAdaptiveState` en desarrollo) |
| `Semaphore` | `core.IntersectionZone` | Limita cuántos vehículos ocupan a la vez la zona de cruce; solo `tryAcquire`, nunca bloquea el tick | Implementado |
| Strategy | `routing` (`RouteStrategy`) | Cambiar cómo calcula su ruta un vehículo: más corta, menos congestionada o aleatoria | En desarrollo |
| Command | `command` (`Command`, `CommandBus`) | La UI envía acciones (pausa, velocidad, aparición de vehículos) y el motor las ejecuta al inicio de cada tick | En desarrollo |
| `Graph<V, E extends Edge<V>>` | `graph` | Grafo dirigido genérico y seguro para lecturas concurrentes | Implementado |
| `PriorityQueue` | `graph.algo.Dijkstra` | Cola ordenada por distancia en el cálculo de caminos mínimos | Implementado |
| `PriorityQueue` | `events`, `distributed` | Cola de eventos y solicitudes de Ricart‑Agrawala ordenadas por `(timestamp, processId)` | En desarrollo |

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
