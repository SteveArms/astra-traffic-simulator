package com.astra.view;

import com.astra.command.CommandBus;
import com.astra.command.Commands;
import com.astra.core.LightView;
import com.astra.core.MutexMode;
import com.astra.core.MutexView;
import com.astra.core.WorldSnapshot;
import com.astra.graph.Intersection;
import com.astra.graph.NodeKind;
import com.astra.graph.RoadGraph;
import com.astra.model.VehicleKind;
import com.astra.model.VehicleView;
import com.astra.traffic.Axis;
import com.astra.util.MapLoader;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.animation.AnimationTimer;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;

/**
 * La ciudad vista desde arriba, escalada para llenar el canvas. La capa estática ({@link CityPainter}) solo se
 * redibuja al cambiar el tamaño; la dinámica dibuja cada frame los semáforos y los vehículos en coordenadas del mundo
 * y, encima y en píxeles de pantalla, la selección, las colas, los candados de Ricart-Agrawala, las etiquetas y el
 * HUD. Clic selecciona un semáforo; clic derecho abre "Forzar verde", que solo envía un comando al bus.
 *
 * @implNote Called from the FX Application Thread.
 */
public final class CityRenderer extends StackPane {

    // Presentación, no simulación: no van a config.properties. Medidas del mundo salvo que digan "px".
    private static final double CROSSING_HALF = CityPainter.LANE_WIDTH;
    private static final double CORNER_HALF = CityPainter.LANE_WIDTH + CityPainter.SIDEWALK;
    private static final double HIT_RADIUS = CORNER_HALF;
    // Margen en px entre la ciudad y el borde del canvas.
    private static final double VIEW_MARGIN = 16;
    // Dos destellos por segundo, por debajo del umbral de 3 por segundo. Sigue el tiempo simulado: con la
    // simulación en pausa deja de parpadear.
    private static final long SIREN_MILLIS = 250;
    private static final double MILLIS_PER_SECOND = 1_000.0;

    // Overlays en px de pantalla.
    private static final double SELECTION_GAP = 2;
    private static final double SELECTION_WIDTH = 2;
    private static final double FULL_BORDER_WIDTH = 1.5;
    private static final double OVERLAY_GAP = 3;
    private static final double HALO_WIDTH = 3;
    private static final Font OVERLAY_FONT = Font.font("System", 10);
    private static final Font HOLDER_FONT = Font.font("System", FontWeight.BOLD, 10);
    private static final Font LABEL_FONT = Font.font("System", 10);
    private static final double LOCK_BODY_WIDTH = 7;
    private static final double LOCK_BODY_HEIGHT = 5;
    private static final double LOCK_BODY_RADIUS = 1.5;
    private static final double LOCK_SHACKLE_WIDTH = 4;
    private static final double LOCK_SHACKLE_HEIGHT = 5;
    private static final double LOCK_STROKE = 1.2;
    private static final double HALF_TURN = 180;
    private static final double VEHICLE_LABEL_RISE = 6;

    private static final Font PAUSE_FONT = Font.font("System", FontWeight.BOLD, 28);
    private static final Font HUD_FONT = Font.font("Monospaced", 12);
    private static final double HUD_MARGIN = 8;
    private static final double HUD_PADDING = 6;
    private static final double HUD_LINE_HEIGHT = 15;
    private static final double HUD_RADIUS = 6;
    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final int SECONDS_PER_MINUTE = 60;

    // Texto claro sobre un halo oscuro: se lee sobre césped, techos o asfalto (>= 4.5:1 contra el halo).
    private static final Color OVERLAY_TEXT = Color.web("#f3f4f6");
    private static final Color OVERLAY_MUTED = Color.web("#d1d5db");
    private static final Color HALO = Color.web("#111317", 0.85);
    private static final Color HUD_SURFACE = Color.web("#1f2937", 0.88);
    private static final Color ZONE_FULL = Color.web("#fb923c");
    // Acento de la interfaz: 5.1:1 contra el fondo oscuro.
    private static final Color SELECTION = Color.web("#4f8cff");
    private static final Color PAUSE_VEIL = Color.web("#030712", 0.6);

    private final MapLoader.Bounds bounds;
    private final ViewTransform view = new ViewTransform();
    private final CityPainter city;
    private final SignalPainter signals;
    private final Supplier<WorldSnapshot> snapshotSource;
    private final CommandBus bus;
    private final ContextMenu lightMenu = new ContextMenu();
    private final Map<String, Intersection> intersections = new HashMap<>();
    private final Canvas staticLayer = new Canvas();
    private final Canvas dynamicLayer = new Canvas();
    private final AnimationTimer timer;
    private final Text measure = new Text();

    private long fpsWindowStart;
    private int framesInWindow;
    private int fps;
    private String selectedId;
    private boolean showLabels;
    private Consumer<String> onIntersectionSelected = id -> { };

    public CityRenderer(RoadGraph graph, MapLoader.Bounds bounds, Supplier<WorldSnapshot> snapshotSource,
            CommandBus bus) {
        Objects.requireNonNull(graph, "graph");
        this.bounds = Objects.requireNonNull(bounds, "bounds");
        this.snapshotSource = Objects.requireNonNull(snapshotSource, "snapshotSource");
        this.bus = Objects.requireNonNull(bus, "bus");
        this.city = new CityPainter(graph, bounds);
        this.signals = new SignalPainter(graph);
        graph.vertices().forEach(i -> intersections.put(i.id(), i));
        measure.setFont(HUD_FONT);
        setOnMouseClicked(this::onClick);
        setOnContextMenuRequested(this::onContextMenu);

        // Si no, el StackPane no puede encogerse por debajo de los Canvas.
        setMinSize(0, 0);
        for (Canvas layer : new Canvas[] {staticLayer, dynamicLayer}) {
            // Sin gestionar: si no, el tamaño del Canvas alimenta el preferido del panel y empuja a los vecinos.
            layer.setManaged(false);
            layer.widthProperty().bind(widthProperty());
            layer.heightProperty().bind(heightProperty());
        }
        staticLayer.widthProperty().addListener((obs, old, now) -> drawStatic());
        staticLayer.heightProperty().addListener((obs, old, now) -> drawStatic());
        getChildren().addAll(staticLayer, dynamicLayer);

        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                drawFrame(now);
            }
        };
    }

    public void start() {
        fpsWindowStart = 0;
        timer.start();
    }

    public void stop() {
        timer.stop();
        lightMenu.hide();
    }

    public void setOnIntersectionSelected(Consumer<String> listener) {
        onIntersectionSelected = Objects.requireNonNull(listener, "listener");
    }

    /** Resalta una intersección elegida desde otro control; no vuelve a notificar. */
    public void select(String intersectionId) {
        selectedId = intersectionId;
    }

    /** Muestra u oculta los ids de nodos y vehículos. */
    public void setShowLabels(boolean show) {
        showLabels = show;
    }

    private void onClick(MouseEvent e) {
        if (e.getButton() != MouseButton.PRIMARY) {
            return;
        }
        String id = lightAt(e.getX(), e.getY());
        if (id != null) {
            choose(id);
        }
    }

    private void onContextMenu(ContextMenuEvent e) {
        String id = lightAt(e.getX(), e.getY());
        if (id == null) {
            return;
        }
        choose(id);
        MenuItem ns = new MenuItem("Forzar verde NS");
        ns.setOnAction(a -> bus.submit(Commands.forceLight(id, Axis.NS)));
        MenuItem ew = new MenuItem("Forzar verde EW");
        ew.setOnAction(a -> bus.submit(Commands.forceLight(id, Axis.EW)));
        lightMenu.getItems().setAll(ns, ew);
        lightMenu.show(this, e.getScreenX(), e.getScreenY());
        e.consume();
    }

    private void choose(String id) {
        selectedId = id;
        onIntersectionSelected.accept(id);
    }

    // (x, y) en píxeles del canvas: se pasa al mundo con la transformación inversa.
    private String lightAt(double x, double y) {
        double worldX = view.toWorldX(x);
        double worldY = view.toWorldY(y);
        for (LightView light : snapshotSource.get().lights()) {
            if (Math.abs(worldX - light.x()) <= HIT_RADIUS && Math.abs(worldY - light.y()) <= HIT_RADIUS) {
                return light.intersectionId();
            }
        }
        return null;
    }

    private void drawStatic() {
        view.fit(staticLayer.getWidth(), staticLayer.getHeight(), bounds.width(), bounds.height(), VIEW_MARGIN);
        GraphicsContext gc = staticLayer.getGraphicsContext2D();
        gc.setFill(CityPainter.BACKGROUND);
        gc.fillRect(0, 0, staticLayer.getWidth(), staticLayer.getHeight());
        gc.save();
        view.apply(gc);
        city.paint(gc);
        gc.restore();
    }

    private void drawFrame(long now) {
        updateFps(now);
        WorldSnapshot snapshot = snapshotSource.get();
        GraphicsContext gc = dynamicLayer.getGraphicsContext2D();
        gc.clearRect(0, 0, dynamicLayer.getWidth(), dynamicLayer.getHeight());

        gc.save();
        view.apply(gc);
        for (LightView light : snapshot.lights()) {
            signals.paint(gc, light);
        }
        boolean sirenRed = (long) (snapshot.simTimeSeconds() * MILLIS_PER_SECOND / SIREN_MILLIS) % 2 == 0;
        for (VehicleView vehicle : snapshot.vehicles()) {
            VehiclePainter.paint(gc, vehicle, sirenRed);
        }
        gc.restore();

        gc.setLineJoin(StrokeLineJoin.ROUND);
        for (LightView light : snapshot.lights()) {
            drawZoneFull(gc, light);
            drawQueues(gc, light);
            if (light.intersectionId().equals(selectedId)) {
                drawSelection(gc, light);
            }
        }
        if (snapshot.mutexMode() == MutexMode.RICART_AGRAWALA) {
            for (MutexView mutex : snapshot.mutexes()) {
                drawLock(gc, mutex);
            }
        }
        if (showLabels) {
            drawLabels(gc, snapshot);
        }
        drawHud(gc, snapshot);
        if (snapshot.paused()) {
            drawPauseOverlay(gc);
        }
    }

    // Demanda por eje junto a la esquina superior derecha: ↕ = NS, ↔ = EW.
    private void drawQueues(GraphicsContext gc, LightView light) {
        gc.setFont(OVERLAY_FONT);
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setTextBaseline(VPos.BOTTOM);
        haloText(gc, "↕%d ↔%d".formatted(light.queueNs(), light.queueEw()), OVERLAY_MUTED,
                view.toScreenX(light.x() + CORNER_HALF) + OVERLAY_GAP,
                view.toScreenY(light.y() - CORNER_HALF) - OVERLAY_GAP);
    }

    // Candado fuera de la esquina superior izquierda: contorno = libre; relleno y con el id = alguien la tiene.
    // Solo en los cruces: en gateways, JUNCTION y playas sería ruido.
    private void drawLock(GraphicsContext gc, MutexView mutex) {
        Intersection at = intersections.get(mutex.intersectionId());
        if (at == null || at.kind() != NodeKind.INTERSECTION) {
            return;
        }
        double right = view.toScreenX(at.x() - CORNER_HALF) - OVERLAY_GAP;
        double bottom = view.toScreenY(at.y() - CORNER_HALF) - OVERLAY_GAP;
        double left = right - LOCK_BODY_WIDTH;
        double bodyTop = bottom - LOCK_BODY_HEIGHT;
        double shackleLeft = left + (LOCK_BODY_WIDTH - LOCK_SHACKLE_WIDTH) / 2;
        Color color = mutex.held() ? OVERLAY_TEXT : OVERLAY_MUTED;
        // Halo primero, para que el candado se lea sobre cualquier fondo.
        gc.setStroke(HALO);
        gc.setLineWidth(LOCK_STROKE + HALO_WIDTH);
        gc.strokeArc(shackleLeft, bodyTop - LOCK_SHACKLE_HEIGHT / 2, LOCK_SHACKLE_WIDTH, LOCK_SHACKLE_HEIGHT,
                0, HALF_TURN, ArcType.OPEN);
        gc.strokeRoundRect(left, bodyTop, LOCK_BODY_WIDTH, LOCK_BODY_HEIGHT, LOCK_BODY_RADIUS, LOCK_BODY_RADIUS);
        gc.setStroke(color);
        gc.setFill(color);
        gc.setLineWidth(LOCK_STROKE);
        gc.strokeArc(shackleLeft, bodyTop - LOCK_SHACKLE_HEIGHT / 2, LOCK_SHACKLE_WIDTH, LOCK_SHACKLE_HEIGHT,
                0, HALF_TURN, ArcType.OPEN);
        if (mutex.held()) {
            gc.fillRoundRect(left, bodyTop, LOCK_BODY_WIDTH, LOCK_BODY_HEIGHT, LOCK_BODY_RADIUS, LOCK_BODY_RADIUS);
            gc.setFont(HOLDER_FONT);
            gc.setTextAlign(TextAlignment.RIGHT);
            gc.setTextBaseline(VPos.BOTTOM);
            haloText(gc, mutex.holder(), OVERLAY_TEXT, left - OVERLAY_GAP, bottom);
        } else {
            gc.strokeRoundRect(left, bodyTop, LOCK_BODY_WIDTH, LOCK_BODY_HEIGHT, LOCK_BODY_RADIUS, LOCK_BODY_RADIUS);
        }
    }

    private void drawSelection(GraphicsContext gc, LightView light) {
        double half = CORNER_HALF * view.scale() + SELECTION_GAP + SELECTION_WIDTH / 2;
        gc.setStroke(SELECTION);
        gc.setLineWidth(SELECTION_WIDTH);
        gc.strokeRect(view.toScreenX(light.x()) - half, view.toScreenY(light.y()) - half, 2 * half, 2 * half);
    }

    // Zona de cruce llena: contorno naranja fino sobre el cuadrado de asfalto.
    private void drawZoneFull(GraphicsContext gc, LightView light) {
        if (!light.zoneFull()) {
            return;
        }
        double half = CROSSING_HALF * view.scale();
        gc.setStroke(ZONE_FULL);
        gc.setLineWidth(FULL_BORDER_WIDTH);
        gc.strokeRect(view.toScreenX(light.x()) - half, view.toScreenY(light.y()) - half, 2 * half, 2 * half);
    }

    private void drawLabels(GraphicsContext gc, WorldSnapshot snapshot) {
        gc.setFont(LABEL_FONT);
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setTextBaseline(VPos.TOP);
        for (Intersection node : intersections.values()) {
            haloText(gc, node.id(), OVERLAY_MUTED, view.toScreenX(node.x() + CORNER_HALF) + OVERLAY_GAP,
                    view.toScreenY(node.y() + CORNER_HALF) + OVERLAY_GAP);
        }
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.BOTTOM);
        for (VehicleView vehicle : snapshot.vehicles()) {
            haloText(gc, vehicle.id(), OVERLAY_TEXT, view.toScreenX(VehiclePainter.centerX(vehicle)),
                    view.toScreenY(VehiclePainter.centerY(vehicle)) - VEHICLE_LABEL_RISE);
        }
    }

    private static void haloText(GraphicsContext gc, String text, Color color, double x, double y) {
        gc.setStroke(HALO);
        gc.setLineWidth(HALO_WIDTH);
        gc.strokeText(text, x, y);
        gc.setFill(color);
        gc.fillText(text, x, y);
    }

    private void drawPauseOverlay(GraphicsContext gc) {
        double width = dynamicLayer.getWidth();
        double height = dynamicLayer.getHeight();
        gc.setFill(PAUSE_VEIL);
        gc.fillRect(0, 0, width, height);
        gc.setFill(OVERLAY_TEXT);
        gc.setFont(PAUSE_FONT);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);
        gc.fillText("PAUSA", width / 2, height / 2);
    }

    // Compacto y semitransparente: tapa lo menos posible de la ciudad.
    private void drawHud(GraphicsContext gc, WorldSnapshot snapshot) {
        long totalSeconds = (long) snapshot.simTimeSeconds();
        String[] lines = {
            "Tick %d · %02d:%02d · %d FPS".formatted(snapshot.tick(), totalSeconds / SECONDS_PER_MINUTE,
                    totalSeconds % SECONDS_PER_MINUTE, fps),
            "Autos %d · Buses %d · Amb. %d".formatted(snapshot.activeCount(VehicleKind.CAR),
                    snapshot.activeCount(VehicleKind.BUS), snapshot.activeCount(VehicleKind.AMBULANCE)),
            "Generados %d · Salidos %d".formatted(snapshot.spawned(), snapshot.despawned()),
        };
        double height = lines.length * HUD_LINE_HEIGHT + 2 * HUD_PADDING;
        double width = widestLine(lines) + 2 * HUD_PADDING;
        gc.setFill(HUD_SURFACE);
        gc.fillRoundRect(HUD_MARGIN, HUD_MARGIN, width, height, HUD_RADIUS * 2, HUD_RADIUS * 2);

        gc.setFill(OVERLAY_TEXT);
        gc.setFont(HUD_FONT);
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setTextBaseline(VPos.TOP);
        for (int i = 0; i < lines.length; i++) {
            gc.fillText(lines[i], HUD_MARGIN + HUD_PADDING, HUD_MARGIN + HUD_PADDING + i * HUD_LINE_HEIGHT);
        }
    }

    private double widestLine(String[] lines) {
        double widest = 0;
        for (String line : lines) {
            measure.setText(line);
            widest = Math.max(widest, measure.getLayoutBounds().getWidth());
        }
        return Math.ceil(widest);
    }

    private void updateFps(long now) {
        if (fpsWindowStart == 0) {
            fpsWindowStart = now;
        }
        framesInWindow++;
        if (now - fpsWindowStart >= NANOS_PER_SECOND) {
            fps = framesInWindow;
            framesInWindow = 0;
            fpsWindowStart = now;
        }
    }
}
