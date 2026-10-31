# CLAUDE.md — ASTRA Traffic Simulator

Guía para Claude Code en este repositorio. Las reglas generales del proyecto (stack,
estructura, concurrencia, patrones, estilo, Git) están en **AGENTS.md** y se importan aquí:

@AGENTS.md

---

## Resumen rápido

- Proyecto: **ASTRA — Asynchronous Simulator of Traffic, Routing & Automata**.
- Java 21 + JavaFX 21 + Maven. Clase principal `com.astra.app.AstraApp` (`module com.astra`).
- Ejecutar: `mvn javafx:run` · Tests: `mvn test` · Siempre desde la raíz del repo.

## Cómo debe trabajar Claude aquí

1. **Lee antes de escribir.** Antes de modificar una clase, revisa su interfaz y quién la
   llama (`grep -rn "NombreClase" src/`). Respeta los contratos de la sección 6 de AGENTS.md.
2. **Cambios pequeños y verificables.** Tras cada cambio: `mvn -q clean compile`; si tocaste
   lógica, `mvn test`. No declares una tarea terminada con el build en rojo.
3. **Concurrencia primero.** En cada método nuevo que toque estado compartido, indica en el
   Javadoc qué hilo lo invoca (`@implNote Called from astra-engine thread`). Si dudas entre
   bloquear o no dentro del tick, usa `tryAcquire`.
4. **Nada de JavaFX en el motor.** Si necesitas mostrar algo nuevo en pantalla, agrégalo al
   `WorldSnapshot` (record inmutable) y dibújalo en `CityRenderer`.
5. **Extender, no ramificar.** Nuevo comportamiento ⇒ nueva clase `LightState`,
   `RouteStrategy` o `Command`. Evita `if/switch` por tipo dentro de clases existentes.
6. **Tests junto con el código.** Toda clase en `graph`, `traffic`, `routing`, `command` o
   `distributed` lleva su test JUnit 5 en el mismo commit o en el siguiente.
7. **Configuración.** Valores numéricos nuevos van a `config.properties` + getter en `SimConfig`.
8. **Logs de evidencia.** No cambies el formato `[LAMPORT N] PROCESS -> ACTION` ni
   `[RICART] ...`; el informe depende de ellos.

## Git (para Claude)

- Trabaja siempre en la rama `feature/*`, `fix/*`, `test/*`, `docs/*` o `chore/*` indicada;
  nunca hagas commit directo en `main` ni en `develop`.
- Mensajes en inglés con Conventional Commits: `feat(traffic): add SmartAdaptiveState`.
- Haz commit solo cuando el usuario lo pida, y `push` solo si lo pide explícitamente.
- No reescribas historia publicada (`push --force`) sin permiso.

## Errores frecuentes a evitar

- `IllegalStateException: Not on FX application thread` → estás tocando un nodo desde el
  motor o un worker. Usa el snapshot.
- `MissingProjectException` → Maven ejecutado fuera de la raíz.
- `JavaFX runtime components are missing` → ejecutar con `mvn javafx:run`, no con `java -jar`.
- Recursos `null` en `getResource(...)` → deben estar en
  `src/main/resources/com/astra/...` y el paquete abierto en `module-info.java`.
- La app no cierra → algún executor sin `shutdownNow()` en `AstraApp.stop()`.

## Referencia rápida de `module-info.java`

```java
module com.astra {
    requires javafx.controls;
    requires com.google.gson;

    exports com.astra.app;
    opens com.astra.graph to com.google.gson;   // deserialización de mapas
    opens com.astra.util to com.google.gson;
}
```

## Estado del plan (actualizar al avanzar)

- [x] Día 1 — Setup Maven/JavaFX 21, `Graph<V,E>`, Dijkstra, MapLoader
- [x] Día 2 — `SimulationEngine`, `WorldSnapshot`, `CityRenderer`
- [x] Día 3 — Semáforos (State) asíncronos + `Semaphore` en intersecciones
- [ ] Día 4 — Estrategias de ruta, Bus/Ambulance, spawner + `Phaser`
- [ ] Día 5 — `CommandBus`, ControlPanel, StatsPanel, SmartAdaptiveState
- [ ] Día 6 — Lamport + Ricart‑Agrawala, `SimLogger`, tests de estrés
- [ ] Día 7 — Informe LaTeX, capturas, README, release v1.0.0