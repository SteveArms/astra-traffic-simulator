# Changelog

Todos los cambios importantes de ASTRA se registran en este archivo.

El formato sigue [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/). El proyecto todavía no
publicó una versión (`pom.xml` dice `0.1.0-SNAPSHOT`), así que todo está en **[Unreleased]**, agrupado
por día del plan y por rama. Las entradas salen de `git log --oneline --no-merges`; el hash corto de cada
commit va al final.

## [Unreleased]

### Documentación completa — rama `docs/full-documentation` (PR #13)

La primera parte se integró a `main` con el PR #13. Después la rama avanzó por *fast-forward* hasta `main`
para incluir el PR #12 y la documentación se actualizó con los autos estacionados.

#### Agregado

- `maven-javadoc-plugin` para generar el Javadoc con `mvn javadoc:javadoc` en un proyecto modular
  (`38c83d0`).
- Documentos en `docs/`: arquitectura (`f0c3193`), concurrencia (`7a11529`), patrones y genéricos
  (`b086d57`), capa distribuida con una traza real (`f3d9d3d`), modelo de simulación (`1b3fadd`), mapas
  (`b4ec2f5`), configuración (`46dc908`), pruebas (`0eea49f`), guía de usuario (`219cdf8`) y cómo
  contribuir (`d9d4ce6`).
- Este `CHANGELOG.md`.

#### Cambiado

- `README.md` reescrito como portada: características, requisitos del curso, controles e índice de la
  documentación (`7e59861`).
- `AGENTS.md` y `CLAUDE.md` alineados con la estructura y los contratos actuales del código.

#### Documentación

- Javadoc completado por paquete, sin cambiar la lógica: `traffic` (`af2dee9`, `645fc5d`), `model`
  (`51f82d2`), `command` (`13b88b1`), `app` (`cfb8c26`), `core` (`32553fb`), `distributed` (`4736b0c`),
  `events` (`d8e19cc`), `view` (`a0c55f9`) y `util` (`f0b426b`).

### Autos estacionados — rama `feature/parked-cars` (PR #12)

#### Agregado

- El snapshot publica cada playa como `ParkingView`, con sus autos y el cajón que ocupan (`2dfb835`).
- Los autos estacionados se dibujan en su cajón, con su color de la calle y el frente hacia el pasillo; una
  playa llena o cerrada lleva un contorno naranja y, con **Mostrar etiquetas**, muestra sus cajones libres
  (`b687161`).

#### Cambiado

- La geometría de las playas pasa a `LotGeometry`, compartida por la capa estática y la dinámica
  (`8c1220d`).

### Mejora de la ciudad — rama `feature/city-upgrade` (PR #11, 2026-11-25)

#### Agregado

- Tipos de nodo `NodeKind` (`INTERSECTION`, `GATEWAY`, `JUNCTION`, `PARKING`) y capacidad de las playas
  (`01d40d3`).
- `MapLoader` lee el tipo, la capacidad y los límites (`bounds`) de los nodos del mapa (`8657a5c`).
- Mapa `city-3x3-v2.json` con gateways y playas (`19fa5f6`).
- Vista: la ciudad se escala según los `bounds` del mapa (`4051cf7`), se dibuja con manzanas, veredas,
  calles y playas (`f58b596`), los vehículos se ven desde arriba (`5b1a783`), los semáforos tienen
  cabezales y los overlays son más discretos (`f8c7814`), y la casilla **Mostrar etiquetas** (`4c65a53`).
- Parámetros de playas y búsqueda de estacionamiento en la configuración (`c1ef99e`).
- `ResourcePool<T>` genérico con `Semaphore` y cajones (`5bf64e7`).
- Propósito de viaje (`TripPurpose`) y tramos para estacionar (`0928abe`).
- `ParkingLot` y `ParkingDirectory` (`04a6a36`).
- Estrategias para cuando la playa está llena: `NearestAlternativeStrategy`, `WaitAtEntranceStrategy` y
  `GiveUpStrategy` (`697f139`).
- Integración de las playas y los viajes con estacionamiento en el motor (`2513d21`).

#### Cambiado

- Los semáforos se ponen solo en nodos `INTERSECTION` (`b3750e8`).
- `city-3x3-v2.json` pasa a ser el mapa por defecto (`47ea0ea`).
- Si el mapa tiene gateways, los vehículos se generan solo entre ellos (`98fee20`), y `SpawnVehicleCmd`
  exige gateways como extremos (`e0dd45f`).
- El panel de semáforos lista solo intersecciones (`f69c47d`).

#### Corregido

- Valores cortados en el panel de estadísticas; la ventana se abre maximizada (`6eed5c1`).

#### Pruebas

- La prueba de seguridad de Ricart‑Agrawala ya no usa `sleep` (`7d4916e`).
- Prueba de integración de estacionamiento en el mapa v2 (`c157019`).

#### Documentación

- El log de cierre usa `main..HEAD` porque `develop` no existe (`f441b0a`).
- Tipos de nodo, `bounds` y el mapa v2 en `AGENTS.md` (`f5829ba`).
- Paquete `parking` y su Strategy en `AGENTS.md` (`156014d`).

### Día 6 — Lamport, Ricart‑Agrawala y `SimLogger` — rama `feature/day6-distributed` (PR #10, 2026-11-25)

#### Agregado

- `SimLogger` asíncrono con salida a archivo (`be8eead`) y las claves `log.console` y `log.level`
  (`bd322ee`).
- Reloj de Lamport por proceso (`2b54b69`).
- `Request` con orden total por timestamp y proceso (`67e8ca9`).
- Mensajes `REQUEST` y `REPLY` de Ricart‑Agrawala (`b8b4147`).
- Latencia y semilla de la red distribuida en la configuración (`117edfc`).
- Red simulada con latencia configurable (`390ad24`) que retiene los mensajes durante la pausa
  (`89281ab`).
- Ricart‑Agrawala con paso de mensajes (`bf2cbae`).
- Mutex Ricart‑Agrawala por intersección (`5a17232`).
- Modo de exclusión mutua y timeout de la sección en la configuración (`c0060d3`).
- Ricart‑Agrawala integrado en el cruce de intersecciones (`c291b59`).
- `SetMutexModeCmd` para cambiar el modo en vivo (`59ab21f`).
- Candados de Ricart‑Agrawala en el mapa y control para cambiar el modo (`9d25d14`).

#### Cambiado

- `System.err` reemplazado por `SimLogger` en toda la app (`733a465`).
- Javadoc ajustado a 120 columnas en `SimLogger` y `NetworkSettings` (`141d47f`).

#### Pruebas

- Formato y concurrencia de `SimLogger` (`f4a6ba7`).
- Entrega, latencia y pausa de la red (`8f6b241`).
- Seguridad y vivacidad de Ricart‑Agrawala (`1a04185`).
- Cruce distribuido con 150 vehículos (`95f9611`).

#### Documentación

- Reglas de commits en español y solo commits locales para los agentes (`fd433b4`).
- Ricart‑Agrawala por mensajes y el mutex por intersección (`dc48de2`).

### Día 5 — Comandos, interfaz y semáforo inteligente — rama `feature/day5-commands-ui` (PR #8, 2026-11-09; PR #9, 2026-11-25)

#### Agregado

- `CommandBus` para la interfaz, seguro entre hilos (`d080324`).
- Pausa y un control de simulación compartido por el motor y los semáforos (`977c708`).
- Comandos de pausa, reanudación, paso y otros (`3c45257`).
- Semáforos inteligentes que alargan el verde según la cola (`21ad1c9`).
- Métricas de tiempo de espera, tiempo de viaje y otras (`d594c21`).
- Panel de control, panel de estadísticas y registro de los comandos aplicados (`c555e63`).
- Generación de vehículos en grupos por intervalo (`80fa42f`).

#### Cambiado

- Mayor ritmo de generación de vehículos (`798b5c8`).

### Día 4 — Rutas y tipos de vehículo — rama `feature/day4-routing-vehicles` (PR #7, 2026-11-03)

#### Agregado

- A* genérico con heurístico (`e515937`).
- `RouteStrategy` con las estrategias más corta, menos congestionada y aleatoria (`22e0fda`).
- Tipos `Bus` y `Ambulance` con sus especificaciones (`74ab75b`).
- Prioridad de emergencia: las ambulancias fuerzan el verde (`9bfeeb1`).
- Generador de vehículos determinista con tipos y estrategias (`ffc5839`).

#### Cambiado

- Los vehículos se actualizan en un grupo de workers en paralelo, sincronizados (`c1937e1`).

### Reglas de intersección — rama `fix/intersection-rules` (PR #6, 2026-10-31)

#### Corregido

- Cambios en los semáforos y las intersecciones; las zonas se dibujan con forma de L (`511b7fe`).

### Ejecutable para Windows — rama `chore/windows-exe` (PR #5, 2026-10-31)

#### Agregado

- `build-exe.bat` para generar el ejecutable del simulador (`ea2b0e7`).

### Día 3 — Semáforos asíncronos — rama `feature/day3-traffic-lights` (PR #4, 2026-10-31)

#### Agregado

- Patrón State en los semáforos (`9c9ca65`).
- Controladores de las luces de los semáforos (`15e5258`).
- Protección de las zonas de conflicto de las intersecciones (`362caa8`).
- Los vehículos se detienen en rojo y mantienen la distancia con el de adelante (`3d0c94e`).
- Semáforos y ocupación de las zonas en el mapa (`44c50e5`).

### Día 2 — Motor y renderizado — rama `feature/day2-engine-render` (PR #3, 2026-10-31)

#### Agregado

- `record` inmutables de `WorldSnapshot` (`7955bb7`).
- `Vehicle` abstracto y `Car`, que siguen una ruta (`dc85d3c`).
- Bucle de simulación de paso fijo en su propio hilo (`eb6755f`).
- Renderizado del mapa con una capa estática y vehículos animados (`2d4ddce`).
- Conexión del motor con el renderizador y un escenario de demostración (`7ca8bb7`).

### Día 1 — Base del proyecto — rama `feature/day1-foundation` (PR #2, 2026-10-31)

#### Agregado

- Proyecto Maven con JavaFX 21 (`324450a`).
- `config.properties` y el cargador inmutable `SimConfig` (`75fb0be`).
- Grafo genérico (`8c8b0c7`).
- Dijkstra genérico con cola de prioridad (`38de705`).
- Cargador de mapas JSON y mapa de ejemplo 3 × 3 (`dfa49d0`).

#### Documentación

- README y Día 1 marcado como completado (`b3585ae`).

### Inicio — `main` y rama `docs/agent-guidelines` (PR #1, 2026-10-30)

#### Agregado

- Commit inicial del repositorio (`adb7991`).

#### Documentación

- `AGENTS.md` con las reglas del proyecto (`1ed6532`).
- `CLAUDE.md`, que importa `AGENTS.md` (`b908787`).

[Unreleased]: https://github.com/SteveArms/astra-traffic-simulator/commits/main
