package com.astra.view;

import com.astra.model.VehicleView;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

/**
 * Vehículos vistos desde arriba, en coordenadas del mundo, en su carril derecho y girados según su rumbo. El
 * sistema local tiene x hacia adelante (el parachoques delantero en x = 0) e y hacia la derecha.
 *
 * @implNote Called from the FX Application Thread.
 */
final class VehiclePainter {

    // Centro del carril derecho: medio carril a la derecha del eje de la calle.
    static final double LANE_OFFSET = CityPainter.LANE_WIDTH / 2;

    private static final double CAR_WIDTH = 8;
    private static final double CAR_RADIUS = 3;
    private static final double BUS_WIDTH = 10;
    private static final double BUS_RADIUS = 2;
    private static final double AMBULANCE_WIDTH = 9;
    private static final double AMBULANCE_RADIUS = 2.5;
    private static final double OUTLINE_WIDTH = 0.6;
    private static final double SIDE_INSET = 1;

    // Proporciones del auto, medidas desde el frente como fracción del largo.
    private static final double HOOD = 0.24;
    private static final double WINDSHIELD = 0.38;
    private static final double ROOF_END = 0.76;
    private static final double REAR_WINDOW = 0.86;
    private static final double HEADLIGHT_LENGTH = 1;
    private static final double HEADLIGHT_WIDTH = 1.6;

    private static final double BUS_WINDSHIELD = 1.6;
    private static final double BUS_WINDOW_STEP = 3.2;
    private static final double BUS_WINDOW_LENGTH = 2.2;
    private static final double BUS_WINDOW_WIDTH = 1.6;
    private static final double BUS_WINDOW_MARGIN = 3;

    private static final double CROSS_ARM = 5;
    private static final double CROSS_THICKNESS = 1.8;
    private static final double LIGHT_BAR_LENGTH = 1.8;
    private static final double LIGHT_BAR_FROM_FRONT = 0.3;

    // Seis colores de carrocería, todos >= 3:1 contra el asfalto #3a3d42.
    private static final Color[] CAR_BODIES = {
        Color.web("#ef6b5e"), Color.web("#6ea8ff"), Color.web("#f3f4f6"),
        Color.web("#c5cad1"), Color.web("#6fd08c"), Color.web("#f2a65a"),
    };
    // El techo de cada uno, un poco más claro que su carrocería.
    private static final Color[] CAR_ROOFS = {
        Color.web("#f4948a"), Color.web("#98c1ff"), Color.web("#ffffff"),
        Color.web("#dde0e4"), Color.web("#97dfad"), Color.web("#f6c08a"),
    };
    private static final Color GLASS = Color.web("#1b2430");
    private static final Color HEADLIGHT = Color.web("#fde68a");
    private static final Color OUTLINE = Color.web("#15171a");
    private static final Color BUS_BODY = Color.web("#f5b301");
    private static final Color BUS_ROOF = Color.web("#f9c93c");
    private static final Color AMBULANCE_BODY = Color.web("#fafafa");
    private static final Color AMBULANCE_CROSS = Color.web("#dc2626");
    private static final Color SIREN_RED = Color.web("#ef4444");
    private static final Color SIREN_BLUE = Color.web("#3b82f6");

    private VehiclePainter() {
    }

    /** @param sirenRed fase de la barra de luces de las ambulancias (alterna con el tiempo simulado) */
    static void paint(GraphicsContext gc, VehicleView vehicle, boolean sirenRed) {
        double heading = vehicle.headingRad();
        // Normal derecha con y hacia abajo.
        double x = vehicle.x() - Math.sin(heading) * LANE_OFFSET;
        double y = vehicle.y() + Math.cos(heading) * LANE_OFFSET;
        gc.save();
        gc.translate(x, y);
        gc.rotate(Math.toDegrees(heading));
        double length = vehicle.length();
        switch (vehicle.kind()) {
            case CAR -> paintCar(gc, length, Math.floorMod(vehicle.id().hashCode(), CAR_BODIES.length));
            case BUS -> paintBus(gc, length);
            case AMBULANCE -> paintAmbulance(gc, length, sirenRed);
        }
        gc.restore();
    }

    /**
     * Auto estacionado con su centro en {@code (centerX, centerY)} y el frente hacia {@code headingRad}; mismo color
     * que tenía en la calle.
     */
    static void paintParked(GraphicsContext gc, String vehicleId, double centerX, double centerY, double headingRad,
            double length) {
        gc.save();
        gc.translate(centerX + Math.cos(headingRad) * length / 2, centerY + Math.sin(headingRad) * length / 2);
        gc.rotate(Math.toDegrees(headingRad));
        paintCar(gc, length, Math.floorMod(vehicleId.hashCode(), CAR_BODIES.length));
        gc.restore();
    }

    /** Centro del vehículo dibujado, para ubicar su etiqueta. */
    static double centerX(VehicleView vehicle) {
        double heading = vehicle.headingRad();
        return vehicle.x() - Math.sin(heading) * LANE_OFFSET - Math.cos(heading) * vehicle.length() / 2;
    }

    static double centerY(VehicleView vehicle) {
        double heading = vehicle.headingRad();
        return vehicle.y() + Math.cos(heading) * LANE_OFFSET - Math.sin(heading) * vehicle.length() / 2;
    }

    private static void paintCar(GraphicsContext gc, double length, int color) {
        double half = CAR_WIDTH / 2;
        body(gc, length, CAR_WIDTH, CAR_RADIUS, CAR_BODIES[color]);
        gc.setFill(GLASS);
        gc.fillRect(-length * WINDSHIELD, -half + SIDE_INSET, length * (WINDSHIELD - HOOD), CAR_WIDTH - 2 * SIDE_INSET);
        gc.fillRect(-length * REAR_WINDOW, -half + SIDE_INSET, length * (REAR_WINDOW - ROOF_END),
                CAR_WIDTH - 2 * SIDE_INSET);
        gc.setFill(CAR_ROOFS[color]);
        gc.fillRect(-length * ROOF_END, -half + SIDE_INSET, length * (ROOF_END - WINDSHIELD),
                CAR_WIDTH - 2 * SIDE_INSET);
        gc.setFill(HEADLIGHT);
        gc.fillRect(-HEADLIGHT_LENGTH, -half + SIDE_INSET / 2, HEADLIGHT_LENGTH, HEADLIGHT_WIDTH);
        gc.fillRect(-HEADLIGHT_LENGTH, half - SIDE_INSET / 2 - HEADLIGHT_WIDTH, HEADLIGHT_LENGTH, HEADLIGHT_WIDTH);
    }

    private static void paintBus(GraphicsContext gc, double length) {
        double half = BUS_WIDTH / 2;
        body(gc, length, BUS_WIDTH, BUS_RADIUS, BUS_BODY);
        gc.setFill(BUS_ROOF);
        gc.fillRect(-length + BUS_WINDOW_MARGIN, -half + BUS_WINDOW_WIDTH + SIDE_INSET,
                length - 2 * BUS_WINDOW_MARGIN, BUS_WIDTH - 2 * (BUS_WINDOW_WIDTH + SIDE_INSET));
        gc.setFill(GLASS);
        gc.fillRect(-BUS_WINDSHIELD - SIDE_INSET / 2, -half + SIDE_INSET, BUS_WINDSHIELD, BUS_WIDTH - 2 * SIDE_INSET);
        // Una fila de ventanas a cada lado.
        for (double front = -BUS_WINDOW_MARGIN; front - BUS_WINDOW_LENGTH >= -length + SIDE_INSET;
                front -= BUS_WINDOW_STEP) {
            gc.fillRect(front - BUS_WINDOW_LENGTH, -half + SIDE_INSET / 2, BUS_WINDOW_LENGTH, BUS_WINDOW_WIDTH);
            gc.fillRect(front - BUS_WINDOW_LENGTH, half - SIDE_INSET / 2 - BUS_WINDOW_WIDTH, BUS_WINDOW_LENGTH,
                    BUS_WINDOW_WIDTH);
        }
    }

    private static void paintAmbulance(GraphicsContext gc, double length, boolean sirenRed) {
        double half = AMBULANCE_WIDTH / 2;
        body(gc, length, AMBULANCE_WIDTH, AMBULANCE_RADIUS, AMBULANCE_BODY);
        gc.setFill(GLASS);
        gc.fillRect(-length * WINDSHIELD, -half + SIDE_INSET, length * (WINDSHIELD - HOOD),
                AMBULANCE_WIDTH - 2 * SIDE_INSET);
        gc.setFill(sirenRed ? SIREN_RED : SIREN_BLUE);
        gc.fillRect(-length * LIGHT_BAR_FROM_FRONT - LIGHT_BAR_LENGTH - length * (WINDSHIELD - HOOD),
                -half + SIDE_INSET, LIGHT_BAR_LENGTH, AMBULANCE_WIDTH - 2 * SIDE_INSET);
        double crossX = -length * (1 + ROOF_END) / 2;
        gc.setFill(AMBULANCE_CROSS);
        gc.fillRect(crossX - CROSS_ARM / 2, -CROSS_THICKNESS / 2, CROSS_ARM, CROSS_THICKNESS);
        gc.fillRect(crossX - CROSS_THICKNESS / 2, -CROSS_ARM / 2, CROSS_THICKNESS, CROSS_ARM);
    }

    // Carrocería con el frente en x = 0 y el resto hacia atrás.
    private static void body(GraphicsContext gc, double length, double width, double radius, Color fill) {
        gc.setFill(fill);
        gc.fillRoundRect(-length, -width / 2, length, width, 2 * radius, 2 * radius);
        gc.setStroke(OUTLINE);
        gc.setLineWidth(OUTLINE_WIDTH);
        gc.strokeRoundRect(-length, -width / 2, length, width, 2 * radius, 2 * radius);
    }
}
