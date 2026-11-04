package com.astra.view;

import com.astra.core.LightView;
import com.astra.core.WorldSnapshot;
import com.astra.graph.Intersection;
import com.astra.graph.Road;
import com.astra.graph.RoadGraph;
import com.astra.model.VehicleKind;
import com.astra.model.VehicleView;
import com.astra.traffic.LightColor;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import javafx.animation.AnimationTimer;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

/**
 * Capa estática (calles) y capa dinámica (semáforos, vehículos y HUD) dibujada desde el último snapshot.
 *
 * @implNote Called from the FX Application Thread.
 */
public final class CityRenderer extends StackPane {

    // Presentación, no simulación: no van a config.properties.
    private static final double LANE_WIDTH = 10;
    private static final double CENTER_LINE_WIDTH = 1;
    private static final double[] CENTER_LINE_DASHES = {6, 6};
    private static final double INTERSECTION_SIZE = 24;
    private static final double LABEL_GAP = 4;
    private static final Font LABEL_FONT = Font.font("System", 12);

    private static final double VEHICLE_WIDTH = 6;
    private static final double VEHICLE_RIGHT_OFFSET = 5;
    private static final double OUTLINE_WIDTH = 1;

    private static final double LIGHT_RADIUS = 4;
    private static final double LIGHT_OFFSET = 8;
    private static final double FULL_BORDER_WIDTH = 2;

    private static final Font HUD_FONT = Font.font("Monospaced", 13);
    private static final double HUD_MARGIN = 8;
    private static final double HUD_PADDING = 8;
    private static final double HUD_LINE_HEIGHT = 16;
    private static final double HUD_WIDTH = 144;
    private static final double HUD_RADIUS = 6;
    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final int SECONDS_PER_MINUTE = 60;

    // Contraste medido: texto >= 4.5:1; calles, vehículos y luces >= 3:1 contra su fondo o su contorno.
    private static final Color BACKGROUND = Color.web("#030712");
    private static final Color ROAD = Color.web("#5A6372");
    private static final Color ROAD_CENTER_LINE = Color.web("#E5E7EB");
    private static final Color INTERSECTION = Color.web("#5A6372");
    private static final Color INTERSECTION_OCCUPIED = Color.web("#8B95A5");
    private static final Color INTERSECTION_FULL_BORDER = Color.web("#FB923C");
    private static final Color OUTLINE = Color.web("#030712");
    private static final Color LABEL_TEXT = Color.web("#9CA3AF");
    private static final Color HUD_SURFACE = Color.web("#1F2937");
    private static final Color HUD_TEXT = Color.web("#F3F4F6");
    private static final Color CAR = Color.web("#93C5FD");
    private static final Color BUS = Color.web("#FCD34D");
    private static final Color AMBULANCE = Color.web("#FCA5A5");
    private static final Color LIGHT_GREEN = Color.web("#4ADE80");
    private static final Color LIGHT_YELLOW = Color.web("#FACC15");
    private static final Color LIGHT_RED = Color.web("#F87171");

    private final RoadGraph graph;
    private final Supplier<WorldSnapshot> snapshotSource;
    private final Map<String, Set<Side>> approaches;
    private final Canvas staticLayer = new Canvas();
    private final Canvas dynamicLayer = new Canvas();
    private final AnimationTimer timer;

    private double offsetX;
    private double offsetY;
    private long fpsWindowStart;
    private int framesInWindow;
    private int fps;

    public CityRenderer(RoadGraph graph, Supplier<WorldSnapshot> snapshotSource) {
        this.graph = Objects.requireNonNull(graph, "graph");
        this.snapshotSource = Objects.requireNonNull(snapshotSource, "snapshotSource");
        this.approaches = approachesOf(graph);

        // Si no, el StackPane no puede encogerse por debajo de los Canvas.
        setMinSize(0, 0);
        for (Canvas layer : new Canvas[] {staticLayer, dynamicLayer}) {
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
    }

    private void drawStatic() {
        centerMap();
        GraphicsContext gc = staticLayer.getGraphicsContext2D();
        gc.setFill(BACKGROUND);
        gc.fillRect(0, 0, staticLayer.getWidth(), staticLayer.getHeight());
        gc.save();
        gc.translate(offsetX, offsetY);

        gc.setLineCap(StrokeLineCap.BUTT);
        for (Road road : graph.edges()) {
            Road reverse = reverseOf(road);
            if (reverse == null) {
                drawRoad(gc, road, road.lanes(), false);
            } else if (road.from().id().compareTo(road.to().id()) < 0) {
                drawRoad(gc, road, road.lanes() + reverse.lanes(), true);
            }
        }

        gc.setFont(LABEL_FONT);
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setTextBaseline(VPos.TOP);
        double half = INTERSECTION_SIZE / 2;
        for (Intersection i : graph.vertices()) {
            gc.setFill(INTERSECTION);
            gc.fillRect(i.x() - half, i.y() - half, INTERSECTION_SIZE, INTERSECTION_SIZE);
            gc.setFill(LABEL_TEXT);
            gc.fillText(i.id(), i.x() + half + LABEL_GAP, i.y() + half + LABEL_GAP);
        }
        gc.restore();
    }

    private void centerMap() {
        double minX = graph.vertices().stream().mapToDouble(Intersection::x).min().orElse(0);
        double maxX = graph.vertices().stream().mapToDouble(Intersection::x).max().orElse(0);
        double minY = graph.vertices().stream().mapToDouble(Intersection::y).min().orElse(0);
        double maxY = graph.vertices().stream().mapToDouble(Intersection::y).max().orElse(0);
        offsetX = (staticLayer.getWidth() - (maxX - minX)) / 2 - minX;
        offsetY = (staticLayer.getHeight() - (maxY - minY)) / 2 - minY;
    }

    private void drawRoad(GraphicsContext gc, Road road, int totalLanes, boolean twoWay) {
        gc.setStroke(ROAD);
        gc.setLineWidth(totalLanes * LANE_WIDTH);
        gc.setLineDashes();
        gc.strokeLine(road.from().x(), road.from().y(), road.to().x(), road.to().y());
        if (twoWay) {
            gc.setStroke(ROAD_CENTER_LINE);
            gc.setLineWidth(CENTER_LINE_WIDTH);
            gc.setLineDashes(CENTER_LINE_DASHES);
            gc.strokeLine(road.from().x(), road.from().y(), road.to().x(), road.to().y());
            gc.setLineDashes();
        }
    }

    private Road reverseOf(Road road) {
        for (Road candidate : graph.outgoing(road.to())) {
            if (candidate.to().equals(road.from())) {
                return candidate;
            }
        }
        return null;
    }

    private void drawFrame(long now) {
        updateFps(now);
        WorldSnapshot snapshot = snapshotSource.get();
        GraphicsContext gc = dynamicLayer.getGraphicsContext2D();
        gc.clearRect(0, 0, dynamicLayer.getWidth(), dynamicLayer.getHeight());
        gc.save();
        gc.translate(offsetX, offsetY);
        for (LightView light : snapshot.lights()) {
            drawZone(gc, light);
            drawLight(gc, light);
        }
        for (VehicleView vehicle : snapshot.vehicles()) {
            drawVehicle(gc, vehicle);
        }
        gc.restore();
        drawHud(gc, snapshot);
    }

    private void drawZone(GraphicsContext gc, LightView light) {
        if (!light.zoneOccupied()) {
            return;
        }
        double half = INTERSECTION_SIZE / 2;
        double left = light.x() - half;
        double top = light.y() - half;
        gc.setFill(INTERSECTION_OCCUPIED);
        gc.fillRect(left, top, INTERSECTION_SIZE, INTERSECTION_SIZE);
        if (light.zoneFull()) {
            // El naranja contrasta poco con el gris claro: lo separa un contorno oscuro.
            double outer = FULL_BORDER_WIDTH / 2 + OUTLINE_WIDTH;
            gc.setStroke(INTERSECTION_FULL_BORDER);
            gc.setLineWidth(FULL_BORDER_WIDTH);
            gc.strokeRect(left - outer, top - outer, INTERSECTION_SIZE + 2 * outer, INTERSECTION_SIZE + 2 * outer);
            gc.setStroke(OUTLINE);
            gc.setLineWidth(OUTLINE_WIDTH);
            gc.strokeRect(left - OUTLINE_WIDTH / 2, top - OUTLINE_WIDTH / 2,
                    INTERSECTION_SIZE + OUTLINE_WIDTH, INTERSECTION_SIZE + OUTLINE_WIDTH);
        }
    }

    // Una luz por cada lado del que llega una calle.
    private void drawLight(GraphicsContext gc, LightView light) {
        for (Side side : approaches.getOrDefault(light.intersectionId(), Set.of())) {
            LightColor color = side.vertical ? light.ns() : light.ew();
            drawLamp(gc, light.x() + side.dx * LIGHT_OFFSET, light.y() + side.dy * LIGHT_OFFSET, color);
        }
    }

    private static Map<String, Set<Side>> approachesOf(RoadGraph graph) {
        Map<String, Set<Side>> sides = new HashMap<>();
        for (Road road : graph.edges()) {
            sides.computeIfAbsent(road.to().id(), id -> EnumSet.noneOf(Side.class)).add(Side.of(road));
        }
        return Map.copyOf(sides);
    }

    private enum Side {
        NORTH(0, -1, true), SOUTH(0, 1, true), WEST(-1, 0, false), EAST(1, 0, false);

        private final int dx;
        private final int dy;
        private final boolean vertical;

        Side(int dx, int dy, boolean vertical) {
            this.dx = dx;
            this.dy = dy;
            this.vertical = vertical;
        }

        // Lado de la intersección destino por el que entra la vía.
        static Side of(Road incoming) {
            double dx = incoming.from().x() - incoming.to().x();
            double dy = incoming.from().y() - incoming.to().y();
            if (Math.abs(dx) >= Math.abs(dy)) {
                return dx < 0 ? WEST : EAST;
            }
            return dy < 0 ? NORTH : SOUTH;
        }
    }

    private void drawLamp(GraphicsContext gc, double x, double y, LightColor color) {
        double diameter = 2 * LIGHT_RADIUS;
        gc.setFill(lampColor(color));
        gc.fillOval(x - LIGHT_RADIUS, y - LIGHT_RADIUS, diameter, diameter);
        gc.setStroke(OUTLINE);
        gc.setLineWidth(OUTLINE_WIDTH);
        gc.strokeOval(x - LIGHT_RADIUS, y - LIGHT_RADIUS, diameter, diameter);
    }

    private static Color lampColor(LightColor color) {
        return switch (color) {
            case GREEN -> LIGHT_GREEN;
            case YELLOW -> LIGHT_YELLOW;
            case RED -> LIGHT_RED;
        };
    }

    private void drawVehicle(GraphicsContext gc, VehicleView vehicle) {
        double heading = vehicle.headingRad();
        // Normal derecha con y hacia abajo.
        double x = vehicle.x() - Math.sin(heading) * VEHICLE_RIGHT_OFFSET;
        double y = vehicle.y() + Math.cos(heading) * VEHICLE_RIGHT_OFFSET;
        gc.save();
        gc.translate(x, y);
        gc.rotate(Math.toDegrees(heading));
        gc.setFill(colorOf(vehicle.kind()));
        // (x, y) es el parachoques delantero: el cuerpo se extiende hacia atrás.
        double length = vehicle.length();
        gc.fillRect(-length, -VEHICLE_WIDTH / 2, length, VEHICLE_WIDTH);
        gc.setStroke(OUTLINE);
        gc.setLineWidth(OUTLINE_WIDTH);
        gc.strokeRect(-length, -VEHICLE_WIDTH / 2, length, VEHICLE_WIDTH);
        gc.restore();
    }

    private static Color colorOf(VehicleKind kind) {
        return switch (kind) {
            case CAR -> CAR;
            case BUS -> BUS;
            case AMBULANCE -> AMBULANCE;
        };
    }

    private void drawHud(GraphicsContext gc, WorldSnapshot snapshot) {
        long totalSeconds = (long) snapshot.simTimeSeconds();
        String[] lines = {
            "Tick      %d".formatted(snapshot.tick()),
            "Tiempo    %02d:%02d".formatted(totalSeconds / SECONDS_PER_MINUTE, totalSeconds % SECONDS_PER_MINUTE),
            "Vehículos %d".formatted(snapshot.vehicleCount()),
            "Semáforos %d".formatted(snapshot.lightCount()),
            "FPS       %d".formatted(fps),
        };
        double height = lines.length * HUD_LINE_HEIGHT + 2 * HUD_PADDING;
        gc.setFill(HUD_SURFACE);
        gc.fillRoundRect(HUD_MARGIN, HUD_MARGIN, HUD_WIDTH, height, HUD_RADIUS * 2, HUD_RADIUS * 2);

        gc.setFill(HUD_TEXT);
        gc.setFont(HUD_FONT);
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setTextBaseline(VPos.TOP);
        for (int i = 0; i < lines.length; i++) {
            gc.fillText(lines[i], HUD_MARGIN + HUD_PADDING, HUD_MARGIN + HUD_PADDING + i * HUD_LINE_HEIGHT);
        }
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
