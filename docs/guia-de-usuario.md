# Guía de usuario

Abre la aplicación con `mvn javafx:run` desde la raíz del repositorio, o con `ASTRA.exe` si generaste el
ejecutable (ver el [README](../README.md#ejecutable-para-windows)). La ventana se abre maximizada y la
simulación arranca sola, con el mapa `city-3x3-v2.json`.

## La ventana

```text
┌──────────────────┬──────────────────────────────────────────┬───────────────────────┐
│ Panel de control │ Ciudad                                   │ Estadísticas          │
│ (260 px)         │ ┌ HUD ─────────────────┐                 │ (320 px)              │
│ Simulación       │ │ Tick · mm:ss · FPS   │                 │ Estadísticas          │
│ Vehículos        │ │ Autos · Buses · Amb. │                 │ Evolución             │
│ Semáforos        │ │ Generados · Salidos  │                 │  · Espera promedio    │
│ Exclusión        │ └──────────────────────┘                 │  · Throughput         │
│ Historial        │                                          │                       │
└──────────────────┴──────────────────────────────────────────┴───────────────────────┘
```

### Panel de control (izquierda)

| Sección | Controles |
| --- | --- |
| **Simulación** | **Pausar** / **Reanudar**; **Paso** (avanza un tick, solo en pausa); el control deslizante **Velocidad**, de ×0.25 a ×4 con marcas en ×0.25, ×0.5, ×1, ×2 y ×4; la casilla **Mostrar etiquetas** (ids de nodos y vehículos) |
| **Vehículos** | **Tipo** (Auto, Bus, Ambulancia), **Ruta** (Más corta, Menos congestionada, Aleatoria), **Generar** y **Generar ×10**. El origen y el destino se sortean entre los gateways |
| **Semáforos** | **Fijo** / **Inteligente** (modo de todos los semáforos), **Intersección** (lista de los cruces con semáforo) y **Verde NS** / **Verde EW** para forzar un eje en la intersección elegida |
| **Exclusión** | **Semaphore** / **Ricart-Agrawala**: cómo se reserva la zona central de cada cruce |
| **Historial** | Los últimos comandos que ejecutó el motor, del más reciente al más antiguo, con el tick (`#1234 Pause`). Un comando que no hizo nada muestra el motivo, por ejemplo `(ignored: not paused)` |

Cada control envía un `Command` al motor; el panel se sincroniza con el estado real cada 100 ms.

### Estadísticas (derecha)

| Fila | Qué muestra |
| --- | --- |
| Autos, Buses, Ambulancias | Vehículos en circulación de cada tipo (los estacionados no cuentan) |
| Llegados | Vehículos que ya salieron de la ciudad |
| Espera promedio | Tiempo medio que pasaron casi detenidos (< 0.5 px/s) los que llegaron |
| Viaje promedio | Duración media del viaje de los que llegaron |
| Throughput | Llegadas por minuto en los últimos 60 s simulados |
| Cola actual / máx. | Vehículos que se acercan a algún cruce (a 120 px o menos), ahora y como máximo |
| Semáforos | Fijo o Inteligente |
| Exclusión | Semaphore o Ricart-Agrawala |
| Mensajes enviados, Mensajes en vuelo | Tráfico de la red simulada de Ricart‑Agrawala |
| Tick, Tiempo simulado | Ticks del motor y tiempo simulado en `mm:ss` |

Debajo, **Evolución** tiene dos gráficas contra el tiempo simulado: espera promedio (s) y throughput
(veh/min). Guardan los últimos 300 puntos, uno por segundo simulado. El panel se actualiza cada 500 ms.
El cálculo de cada métrica está en [Simulación](simulacion.md#estadísticas).

### HUD

En la esquina superior izquierda de la ciudad:

```text
Tick <ticks> · <mm:ss simulado> · <cuadros por segundo> FPS
Autos <n> · Buses <n> · Amb. <n>
Generados <n> · Salidos <n>
```

En pausa, la ciudad se oscurece y aparece **PAUSA** en el centro.

## Qué se ve en el mapa

| Elemento | Cómo se ve |
| --- | --- |
| Manzanas | Césped con edificios vistos desde arriba |
| Calles | Asfalto con doble línea amarilla en el centro, líneas de borde, líneas de detención y pasos de cebra en los cruces |
| Gateways | La calle se desvanece hacia el borde del mapa, con una flecha de doble punta: por ahí entran y salen los vehículos |
| Semáforos | Un cabezal por acceso, en la esquina derecha de quien llega, con tres luces; solo está encendida la activa: rojo, amarillo o verde |
| Demanda por eje | `↕3 ↔1` junto a la esquina superior derecha de cada cruce con semáforo: vehículos que se acercan por el eje NS (`↕`) y EW (`↔`). Es la "cola" que usa el modo Inteligente |
| Zona llena | Contorno naranja sobre el cuadrado del cruce cuando tiene tantos vehículos dentro como su capacidad (2) |
| Selección | Cuadrado azul alrededor del cruce elegido |
| Candados | Solo en modo Ricart-Agrawala, fuera de la esquina superior izquierda de cada cruce: contorno = nadie tiene la sección; relleno y con el id del vehículo = ese vehículo la tiene |
| Playas | Rectángulos de asfalto con los cajones pintados, unidos a la calle por su entrada, con un letrero azul con una **P** blanca |
| Autos estacionados | Cada uno en su cajón, con el frente hacia el pasillo y el mismo color que tenía en la calle |
| Playa llena o cerrada | Contorno naranja alrededor de la playa |
| Autos | Seis colores de carrocería, con techo, parabrisas y faros |
| Buses | Amarillos, más largos, con una fila de ventanas a cada lado |
| Ambulancias | Blancas con una cruz roja y una barra de luces que alterna rojo y azul dos veces por segundo de tiempo simulado (se detiene en pausa) |
| Etiquetas | Con **Mostrar etiquetas**, el id de cada nodo (`I11`, `G-N0`, `P00` …) y de cada vehículo (`CAR-12`, `BUS-3`, `AMB-1`); al fondo de cada playa, sus cajones libres (`<libres>/<capacidad> libres`) o `Cerrada` |

## Controles con el mouse y el teclado

| Control | Acción |
| --- | --- |
| `Espacio` | Pausar o reanudar |
| `S` | Avanzar un tick (solo en pausa) |
| `+` | Duplicar la velocidad (hasta ×4) |
| `-` o `−` | Dividir la velocidad por 2 (hasta ×0.25) |
| Clic en un cruce con semáforo | Seleccionarlo (también se selecciona en la lista **Intersección**) |
| Clic derecho en un cruce con semáforo | Menú **Forzar verde NS** / **Forzar verde EW** |

Si un botón o una lista tiene el foco, `Espacio` hace su acción propia (por ejemplo, pulsa el botón);
los demás atajos funcionan siempre.

## Recorrido 1: comparar Fijo e Inteligente

1. Deja correr la simulación unos minutos de tiempo simulado en modo **Fijo**. Puedes subir la
   velocidad a ×4 con `+`.
2. Anota **Espera promedio** y mira la forma de la gráfica de espera.
3. Observa los contadores `↕` y `↔` de los cruces centrales: en un eje en verde con 5 o más vehículos
   acercándose y menos en el otro, el modo Inteligente alargaría el verde.
4. Elige **Inteligente** en la sección **Semáforos**. El historial muestra `SetLightMode(SMART)`. Cada
   semáforo aplica el modo en su próximo verde.
5. Compara la espera promedio y la gráfica después de otro rato. Como los promedios son acumulados,
   el cambio se nota de a poco.
6. Para ver cada extensión, cierra la aplicación, pon `log.level=DEBUG` en `config.properties` y vuelve a
   abrirla: en `logs/astra.log` aparece cada transición con la forma
   `HH:mm:ss.SSS [LIGHT] <cruce> <estado> -> <estado>`; una extensión pasa de `SMART-NS` a `SMART-NS+1`.

Para una comparación reproducible, `SmartVsFixedIntegrationTest` corre los dos modos con las mismas
semillas (ver [Pruebas](pruebas.md)).

## Recorrido 2: comparar Semaphore y Ricart‑Agrawala

1. Con el modo **Semaphore**, mira un cruce cargado: pueden entrar hasta 2 vehículos a la vez desde
   accesos distintos, y el contorno naranja indica la zona llena. **Mensajes enviados** no cambia.
2. Elige **Ricart-Agrawala** en la sección **Exclusión**. El historial muestra
   `SetMutexMode(RICART_AGRAWALA)`.
3. Aparecen los candados. Cuando un vehículo está por cruzar, el candado de ese cruce se rellena y
   muestra su id: solo cruza el primero de un acceso a la vez.
4. **Mensajes enviados** empieza a crecer: cada entrada a un cruce de 4 accesos cuesta 6 mensajes,
   2(N − 1). **Mensajes en vuelo** muestra los que todavía no llegaron.
5. Abre `logs/astra.log` y busca las líneas `[LAMPORT N] ...` y `[RICART] ... requested access at`,
   `ENTERED critical section` y `released critical section`. La
   [Capa distribuida](capa-distribuida.md#traza-real) explica cómo leerlas.
6. Vuelve a **Semaphore**: el motor cancela las peticiones pendientes y nadie queda esperando.

## Recorrido 3: playas llenas y estrategias de búsqueda

**Pendiente:** cerrar una playa desde la interfaz. `ParkingLot.setOpen(false)` existe y la cierra (rechaza
a los que llegan y deja salir a los que estaban), pero ningún comando ni control de la interfaz lo llama:
hoy solo lo usan los tests. Si una playa se cerrara, el mapa ya la mostraría con contorno naranja y la
etiqueta `Cerrada`. Tampoco se muestran las métricas de estacionamiento (búsqueda media, rechazos y
ocupación), que sí se calculan.

Lo que se puede hacer hoy es comparar las estrategias cuando las playas se llenan:

1. Cierra la aplicación y edita `config.properties`. Para que las playas se llenen antes, sube
   `parking.tripWeight` (por ejemplo a `0.9`) y `parking.minStaySeconds` / `parking.maxStaySeconds`.
2. Elige la estrategia con `parking.searchStrategy`:
   - `NEAREST_ALTERNATIVE`: el auto va a la playa con lugar más cercana.
   - `WAIT_AT_ENTRANCE`: el auto se queda detenido en la entrada de la playa hasta
     `parking.waitAtEntranceSeconds` (8 s) y después busca otra.
   - `GIVE_UP`: el auto sale enseguida por el gateway más cercano.
3. Abre la aplicación con **Mostrar etiquetas** activado. Cada playa (`P00` … `P11`) muestra sus
   cajones libres y los autos aparecen en su cajón al estacionar. Cuando una se llena, la rodea un
   contorno naranja.
4. Sigue a los autos que llegan a una playa llena. Con `WAIT_AT_ENTRANCE` se ven detenidos al final de
   la entrada; con las otras dos, dan la vuelta y siguen.
5. Tras `parking.maxSearchAttempts` (2) intentos fallidos, cualquier estrategia se rinde.

El comportamiento completo, con playas cerradas, lo comprueban `ParkingSearchStrategyTest` y
`ParkingIntegrationTest.fullLotsMakeVehiclesSearchAndCountRejections`.
