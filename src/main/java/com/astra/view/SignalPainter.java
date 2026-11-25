package com.astra.view;

import com.astra.core.LightView;
import com.astra.graph.Intersection;
import com.astra.graph.Road;
import com.astra.graph.RoadGraph;
import com.astra.traffic.Axis;
import com.astra.traffic.LightColor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

/**
 * Cabezales de semáforo vistos desde arriba, en coordenadas del mundo: uno por acceso, en la esquina derecha de quien
 * llega y orientado hacia el tráfico, con tres luces y solo la activa encendida.
 *
 * @implNote Called from the FX Application Thread.
 */
final class SignalPainter {

    private static final double CORNER = CityPainter.LANE_WIDTH + CityPainter.SIDEWALK / 2;
    private static final double HEAD_DEPTH = 5;
    private static final double HEAD_LENGTH = 13;
    private static final double HEAD_RADIUS = 1.5;
    private static final double LAMP_RADIUS = 1.6;
    private static final double LAMP_SPACING = 4;

    private static final Color HEAD = Color.web("#111317");
    private static final Color LAMP_OFF = Color.web("#33363c");
    private static final Color LAMP_RED = Color.web("#f87171");
    private static final Color LAMP_YELLOW = Color.web("#facc15");
    private static final Color LAMP_GREEN = Color.web("#4ade80");

    /** Acceso a un cruce: dirección unitaria de quien llega y su eje. */
    private record Approach(double ux, double uy, Axis axis) {
    }

    private final Map<String, List<Approach>> approaches = new HashMap<>();

    SignalPainter(RoadGraph graph) {
        for (Road road : graph.edges()) {
            if (road.from().isParking()) {
                continue;
            }
            Intersection to = road.to();
            double length = road.length();
            approaches.computeIfAbsent(to.id(), id -> new ArrayList<>()).add(new Approach(
                    (to.x() - road.from().x()) / length, (to.y() - road.from().y()) / length, Axis.of(road)));
        }
    }

    void paint(GraphicsContext gc, LightView light) {
        for (Approach a : approaches.getOrDefault(light.intersectionId(), List.of())) {
            // Derecha de quien llega, con y hacia abajo.
            double rx = -a.uy();
            double ry = a.ux();
            gc.save();
            gc.translate(light.x() - a.ux() * CORNER + rx * CORNER, light.y() - a.uy() * CORNER + ry * CORNER);
            gc.rotate(Math.toDegrees(Math.atan2(a.uy(), a.ux())));
            gc.setFill(HEAD);
            gc.fillRoundRect(-HEAD_DEPTH / 2, -HEAD_LENGTH / 2, HEAD_DEPTH, HEAD_LENGTH, 2 * HEAD_RADIUS,
                    2 * HEAD_RADIUS);
            LightColor active = a.axis() == Axis.NS ? light.ns() : light.ew();
            lamp(gc, -LAMP_SPACING, active == LightColor.RED ? LAMP_RED : LAMP_OFF);
            lamp(gc, 0, active == LightColor.YELLOW ? LAMP_YELLOW : LAMP_OFF);
            lamp(gc, LAMP_SPACING, active == LightColor.GREEN ? LAMP_GREEN : LAMP_OFF);
            gc.restore();
        }
    }

    private static void lamp(GraphicsContext gc, double across, Color color) {
        gc.setFill(color);
        gc.fillOval(-LAMP_RADIUS, across - LAMP_RADIUS, 2 * LAMP_RADIUS, 2 * LAMP_RADIUS);
    }
}
