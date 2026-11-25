package com.astra.view;

import com.astra.graph.Intersection;
import com.astra.graph.NodeKind;
import com.astra.graph.Road;
import com.astra.graph.RoadGraph;
import com.astra.util.MapLoader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;
import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

/**
 * Capa estática de la ciudad vista desde arriba, en coordenadas del mundo: manzanas con césped y edificios, veredas,
 * calzadas con sus marcas, cruces, salidas de la ciudad y playas. Toda la geometría (incluidos los edificios, que
 * salen de una semilla por manzana) se calcula una vez en el constructor; {@link #paint} solo dibuja.
 *
 * @implNote Called from the FX Application Thread.
 */
final class CityPainter {

    // Medidas del mundo (presentación, no simulación). La calzada de doble sentido mide 2 x LANE_WIDTH.
    static final double LANE_WIDTH = 14;
    static final double SIDEWALK = 8;
    private static final double CORNER_RADIUS = 12;
    private static final double CENTER_LINE_OFFSET = 1;
    private static final double MARK_WIDTH = 0.8;
    private static final double EDGE_LINE_INSET = 1.5;
    private static final double STOP_LINE_GAP = 2;
    private static final double STOP_LINE_WIDTH = 2;
    private static final double ZEBRA_STRIPE = 2.4;
    private static final double ZEBRA_INSET = 1;
    private static final double GATEWAY_FADE = 60;
    private static final double ARROW_DISTANCE = 30;
    private static final double ARROW_HALF_LENGTH = 6;
    private static final double ARROW_HEAD = 2.5;
    private static final double ARROW_WIDTH = 1.2;

    private static final double BUILDING_CELL = 46;
    private static final double BUILDING_SETBACK = 4;
    private static final double BUILDING_GAP = 3;
    private static final double BUILDING_JITTER = 6;
    private static final double BUILDING_MIN = 10;
    private static final double GARDEN_CHANCE = 0.12;
    private static final double SHADOW_OFFSET = 2.5;
    private static final double ROOF_INSET = 3;
    private static final double RESERVED_MARGIN = 6;
    private static final long BLOCK_SEED = 0x5EEDL;

    private static final double STALL_WIDTH = 10;
    private static final double STALL_DEPTH = 16;
    private static final double AISLE = 14;
    private static final double LOT_PAD = 4;
    private static final double SIGN_SIZE = 12;
    private static final double SIGN_RADIUS = 3;
    private static final double SIGN_GAP = 4;
    private static final Font SIGN_FONT = Font.font("System", FontWeight.BOLD, 10);

    static final Color BACKGROUND = Color.web("#1e1f24");
    private static final Color GRASS = Color.web("#55684a");
    private static final Color SIDEWALK_COLOR = Color.web("#b4b7bb");
    static final Color ASPHALT = Color.web("#3a3d42");
    private static final Color CENTER_LINE = Color.web("#e3b93e");
    private static final Color WHITE_PAINT = Color.web("#eceef0");
    private static final Color ARROW = Color.web("#eceef0", 0.75);
    private static final Color SHADOW = Color.web("#000000", 0.28);
    private static final Color BACKGROUND_CLEAR = Color.web("#1e1f24", 0);
    // Techos: cuatro grises y dos terracotas; cada uno con un tono interior un poco más claro.
    private static final Color[] ROOFS = {
        Color.web("#8a9099"), Color.web("#9ea4ab"), Color.web("#737a83"), Color.web("#aeb2b8"),
        Color.web("#b2644a"), Color.web("#9c5641"),
    };
    private static final Color[] ROOF_TOPS = {
        Color.web("#959ba3"), Color.web("#aaafb5"), Color.web("#7f858e"), Color.web("#b9bdc2"),
        Color.web("#bd7058"), Color.web("#a8614c"),
    };
    // Azul de señal de estacionamiento con "P" blanca (contraste 5.2:1).
    private static final Color PARKING_SIGN = Color.web("#2563eb");

    private record Segment(Intersection a, Intersection b, double half) {
    }

    private record Block(double x, double y, double width, double height, boolean[] rounded) {
    }

    private record Building(double x, double y, double width, double height, int palette) {
    }

    /** Playa: {@code (dx, dy)} apunta de su entrada hacia adentro de la manzana. */
    private record Lot(Intersection parking, Intersection junction, double dx, double dy, double depth,
            double width, int capacity) {
    }

    private record Box(double minX, double minY, double maxX, double maxY) {
        boolean overlaps(Box other) {
            return minX < other.maxX && other.minX < maxX && minY < other.maxY && other.minY < maxY;
        }
    }

    private final MapLoader.Bounds bounds;
    private final List<Segment> streets = new ArrayList<>();
    private final List<Block> blocks = new ArrayList<>();
    private final List<Building> buildings = new ArrayList<>();
    private final List<Lot> lots = new ArrayList<>();
    private final List<Intersection> crossings;
    private final List<Intersection> gateways;
    private final RoadGraph graph;

    CityPainter(RoadGraph graph, MapLoader.Bounds bounds) {
        this.graph = graph;
        this.bounds = bounds;
        this.crossings = graph.vertices().stream()
                .filter(i -> i.kind() == NodeKind.INTERSECTION)
                .sorted(Comparator.comparing(Intersection::id))
                .toList();
        this.gateways = graph.vertices().stream().filter(Intersection::isGateway).toList();
        collectStreets();
        collectLots();
        collectBlocks();
    }

    void paint(GraphicsContext gc) {
        gc.setLineCap(StrokeLineCap.BUTT);
        gc.setLineDashes();
        gc.setFill(SIDEWALK_COLOR);
        gc.fillRect(0, 0, bounds.width(), bounds.height());
        paintBlocks(gc);
        paintBuildings(gc);
        paintLots(gc);
        paintStreets(gc);
        paintMarkings(gc);
        paintCrossings(gc);
        paintGateways(gc);
        paintSigns(gc);
    }

    // ---------------------------------------------------------------- geometría

    // Un segmento por calle (los dos sentidos juntos); los accesos a las playas los dibuja paintLots.
    private void collectStreets() {
        for (Road road : graph.edges()) {
            if (road.from().isParking() || road.to().isParking()) {
                continue;
            }
            Road reverse = reverseOf(road);
            int lanes = road.lanes() + (reverse == null ? 0 : reverse.lanes());
            if (reverse == null || road.from().id().compareTo(road.to().id()) < 0) {
                streets.add(new Segment(road.from(), road.to(), lanes * LANE_WIDTH / 2));
            }
        }
    }

    private void collectLots() {
        for (Intersection parking : graph.vertices()) {
            if (!parking.isParking()) {
                continue;
            }
            Intersection junction = graph.outgoing(parking).get(0).to();
            double length = parking.distanceTo(junction);
            double dx = (parking.x() - junction.x()) / length;
            double dy = (parking.y() - junction.y()) / length;
            int perSide = (parking.capacity() + 1) / 2;
            double depth = perSide * STALL_WIDTH + 2 * LOT_PAD;
            double width = 2 * STALL_DEPTH + AISLE;
            lots.add(new Lot(parking, junction, dx, dy, depth, width, parking.capacity()));
        }
    }

    // Las manzanas son las celdas que dejan las líneas de los cruces y el borde del mapa.
    private void collectBlocks() {
        TreeSet<Double> xs = new TreeSet<>(List.of(0.0, bounds.width()));
        TreeSet<Double> ys = new TreeSet<>(List.of(0.0, bounds.height()));
        crossings.forEach(i -> {
            xs.add(i.x());
            ys.add(i.y());
        });
        List<Double> columns = new ArrayList<>(xs);
        List<Double> rows = new ArrayList<>(ys);
        List<Box> reserved = reservedAreas();
        for (int r = 0; r + 1 < rows.size(); r++) {
            for (int c = 0; c + 1 < columns.size(); c++) {
                double x0 = columns.get(c);
                double x1 = columns.get(c + 1);
                double y0 = rows.get(r);
                double y1 = rows.get(r + 1);
                double top = insetAlong(false, y0, x0, x1);
                double bottom = insetAlong(false, y1, x0, x1);
                double left = insetAlong(true, x0, y0, y1);
                double right = insetAlong(true, x1, y0, y1);
                double width = x1 - x0 - left - right;
                double height = y1 - y0 - top - bottom;
                if (width <= 0 || height <= 0) {
                    continue;
                }
                // Esquina redondeada solo donde se cruzan dos veredas: arriba-izq, arriba-der, abajo-der, abajo-izq.
                boolean[] rounded = {top > 0 && left > 0, top > 0 && right > 0, bottom > 0 && right > 0,
                    bottom > 0 && left > 0};
                var block = new Block(x0 + left, y0 + top, width, height, rounded);
                blocks.add(block);
                addBuildings(block, new Random(BLOCK_SEED * 31 + r * 1_009L + c), reserved);
            }
        }
    }

    // Distancia que ocupan calzada y vereda sobre un lado de una celda, o 0 si por ese lado no pasa una calle.
    private double insetAlong(boolean vertical, double line, double from, double to) {
        double middle = (from + to) / 2;
        for (Segment s : streets) {
            boolean onLine = vertical
                    ? s.a().x() == line && s.b().x() == line
                    : s.a().y() == line && s.b().y() == line;
            if (!onLine) {
                continue;
            }
            double lo = vertical ? Math.min(s.a().y(), s.b().y()) : Math.min(s.a().x(), s.b().x());
            double hi = vertical ? Math.max(s.a().y(), s.b().y()) : Math.max(s.a().x(), s.b().x());
            if (lo <= middle && middle <= hi) {
                return s.half() + SIDEWALK;
            }
        }
        return 0;
    }

    private List<Box> reservedAreas() {
        List<Box> reserved = new ArrayList<>();
        for (Lot lot : lots) {
            double nx = -lot.dy();
            double ny = lot.dx();
            Intersection p = lot.parking();
            Intersection j = lot.junction();
            double halfLot = lot.width() / 2;
            double farX = p.x() + lot.dx() * lot.depth();
            double farY = p.y() + lot.dy() * lot.depth();
            // Los 4 vértices de la playa y los 4 de su entrada.
            double[] xs = {
                p.x() + nx * halfLot, p.x() - nx * halfLot, farX + nx * halfLot, farX - nx * halfLot,
                j.x() + nx * LANE_WIDTH, j.x() - nx * LANE_WIDTH, p.x() + nx * LANE_WIDTH, p.x() - nx * LANE_WIDTH,
            };
            double[] ys = {
                p.y() + ny * halfLot, p.y() - ny * halfLot, farY + ny * halfLot, farY - ny * halfLot,
                j.y() + ny * LANE_WIDTH, j.y() - ny * LANE_WIDTH, p.y() + ny * LANE_WIDTH, p.y() - ny * LANE_WIDTH,
            };
            reserved.add(new Box(min(xs) - RESERVED_MARGIN, min(ys) - RESERVED_MARGIN,
                    max(xs) + RESERVED_MARGIN, max(ys) + RESERVED_MARGIN));
        }
        return reserved;
    }

    private void addBuildings(Block block, Random random, List<Box> reserved) {
        double innerX = block.x() + BUILDING_SETBACK;
        double innerY = block.y() + BUILDING_SETBACK;
        double innerW = block.width() - 2 * BUILDING_SETBACK;
        double innerH = block.height() - 2 * BUILDING_SETBACK;
        int nx = Math.max(1, (int) Math.round(innerW / BUILDING_CELL));
        int ny = Math.max(1, (int) Math.round(innerH / BUILDING_CELL));
        double cellW = innerW / nx;
        double cellH = innerH / ny;
        for (int j = 0; j < ny; j++) {
            for (int i = 0; i < nx; i++) {
                // Se sortea todo aunque la celda se descarte: la secuencia no depende de las playas.
                boolean garden = random.nextDouble() < GARDEN_CHANCE;
                double left = BUILDING_GAP + random.nextDouble() * BUILDING_JITTER;
                double right = BUILDING_GAP + random.nextDouble() * BUILDING_JITTER;
                double top = BUILDING_GAP + random.nextDouble() * BUILDING_JITTER;
                double bottom = BUILDING_GAP + random.nextDouble() * BUILDING_JITTER;
                int palette = random.nextInt(ROOFS.length);
                double x = innerX + i * cellW + left;
                double y = innerY + j * cellH + top;
                double w = cellW - left - right;
                double h = cellH - top - bottom;
                if (garden || w < BUILDING_MIN || h < BUILDING_MIN) {
                    continue;
                }
                var box = new Box(x, y, x + w, y + h);
                if (reserved.stream().noneMatch(box::overlaps)) {
                    buildings.add(new Building(x, y, w, h, palette));
                }
            }
        }
    }

    // ---------------------------------------------------------------- dibujo

    private void paintBlocks(GraphicsContext gc) {
        gc.setFill(GRASS);
        for (Block b : blocks) {
            roundedRect(gc, b.x(), b.y(), b.width(), b.height(), b.rounded());
            gc.fill();
        }
    }

    private void paintBuildings(GraphicsContext gc) {
        gc.setFill(SHADOW);
        for (Building b : buildings) {
            gc.fillRect(b.x() + SHADOW_OFFSET, b.y() + SHADOW_OFFSET, b.width(), b.height());
        }
        for (Building b : buildings) {
            gc.setFill(ROOFS[b.palette()]);
            gc.fillRect(b.x(), b.y(), b.width(), b.height());
            gc.setFill(ROOF_TOPS[b.palette()]);
            gc.fillRect(b.x() + ROOF_INSET, b.y() + ROOF_INSET, b.width() - 2 * ROOF_INSET,
                    b.height() - 2 * ROOF_INSET);
        }
    }

    private void paintLots(GraphicsContext gc) {
        for (Lot lot : lots) {
            Intersection p = lot.parking();
            Intersection j = lot.junction();
            double start = LANE_WIDTH - EDGE_LINE_INSET - MARK_WIDTH;
            // Entrada: desde el borde de la calzada hasta la playa.
            gc.setStroke(ASPHALT);
            gc.setLineWidth(2 * LANE_WIDTH);
            gc.strokeLine(j.x() + lot.dx() * start, j.y() + lot.dy() * start, p.x(), p.y());

            gc.save();
            gc.translate(p.x(), p.y());
            gc.rotate(Math.toDegrees(Math.atan2(lot.dy(), lot.dx())));
            // Sistema local: x hacia adentro de la manzana, y a lo ancho de la playa.
            double half = lot.width() / 2;
            gc.setFill(ASPHALT);
            gc.fillRect(0, -half, lot.depth(), lot.width());
            gc.setStroke(WHITE_PAINT);
            gc.setLineWidth(MARK_WIDTH);
            int sideA = (lot.capacity() + 1) / 2;
            int sideB = lot.capacity() / 2;
            paintStalls(gc, sideA, -half, -half + STALL_DEPTH);
            paintStalls(gc, sideB, half - STALL_DEPTH, half);
            gc.restore();
        }
    }

    private static void paintStalls(GraphicsContext gc, int stalls, double y0, double y1) {
        for (int k = 0; k <= stalls; k++) {
            double x = LOT_PAD + k * STALL_WIDTH;
            gc.strokeLine(x, y0, x, y1);
        }
        if (stalls > 0) {
            double inner = y0 < 0 ? y1 : y0;
            gc.strokeLine(LOT_PAD, inner, LOT_PAD + stalls * STALL_WIDTH, inner);
        }
    }

    private void paintStreets(GraphicsContext gc) {
        gc.setStroke(ASPHALT);
        for (Segment s : streets) {
            gc.setLineWidth(2 * s.half());
            gc.strokeLine(s.a().x(), s.a().y(), s.b().x(), s.b().y());
        }
        gc.setFill(ASPHALT);
        for (Intersection i : crossings) {
            double half = LANE_WIDTH;
            gc.fillRect(i.x() - half, i.y() - half, 2 * half, 2 * half);
        }
    }

    private void paintMarkings(GraphicsContext gc) {
        gc.setLineWidth(MARK_WIDTH);
        for (Segment s : streets) {
            double length = s.a().distanceTo(s.b());
            double ux = (s.b().x() - s.a().x()) / length;
            double uy = (s.b().y() - s.a().y()) / length;
            double nx = -uy;
            double ny = ux;
            double centerTrimA = centerTrim(s.a(), s.half());
            double centerTrimB = centerTrim(s.b(), s.half());
            if (length > centerTrimA + centerTrimB) {
                gc.setStroke(CENTER_LINE);
                for (int side = -1; side <= 1; side += 2) {
                    double off = side * CENTER_LINE_OFFSET;
                    gc.strokeLine(s.a().x() + ux * centerTrimA + nx * off, s.a().y() + uy * centerTrimA + ny * off,
                            s.b().x() - ux * centerTrimB + nx * off, s.b().y() - uy * centerTrimB + ny * off);
                }
            }
            double edgeTrimA = edgeTrim(s.a(), s.half());
            double edgeTrimB = edgeTrim(s.b(), s.half());
            if (length > edgeTrimA + edgeTrimB) {
                gc.setStroke(WHITE_PAINT);
                for (int side = -1; side <= 1; side += 2) {
                    double off = side * (s.half() - EDGE_LINE_INSET);
                    gc.strokeLine(s.a().x() + ux * edgeTrimA + nx * off, s.a().y() + uy * edgeTrimA + ny * off,
                            s.b().x() - ux * edgeTrimB + nx * off, s.b().y() - uy * edgeTrimB + ny * off);
                }
            }
        }
    }

    // En cada acceso a un cruce: paso de cebra sobre la franja de la vereda transversal y línea de parada antes.
    private void paintCrossings(GraphicsContext gc) {
        gc.setFill(WHITE_PAINT);
        for (Intersection at : crossings) {
            for (Road incoming : incomingStreets(at)) {
                double length = incoming.length();
                double ux = (at.x() - incoming.from().x()) / length;
                double uy = (at.y() - incoming.from().y()) / length;
                double half = LANE_WIDTH;
                double near = half + ZEBRA_INSET;
                double far = half + SIDEWALK - ZEBRA_INSET;
                for (double across = -half + ZEBRA_INSET; across + ZEBRA_STRIPE <= half; across += 2 * ZEBRA_STRIPE) {
                    fillOriented(gc, at, ux, uy, near, far, across, across + ZEBRA_STRIPE);
                }
                // Línea de parada solo sobre el carril de llegada (el derecho).
                double stop = half + SIDEWALK + STOP_LINE_GAP;
                fillOriented(gc, at, ux, uy, stop, stop + STOP_LINE_WIDTH, 0, half - EDGE_LINE_INSET);
            }
        }
    }

    // Rectángulo en el marco de un acceso: {@code along} se mide desde el centro del cruce hacia atrás y
    // {@code across} hacia la derecha de quien llega.
    private static void fillOriented(GraphicsContext gc, Intersection at, double ux, double uy, double along0,
            double along1, double across0, double across1) {
        double rx = -uy;
        double ry = ux;
        double[] xs = new double[4];
        double[] ys = new double[4];
        double[][] corners = {{along0, across0}, {along1, across0}, {along1, across1}, {along0, across1}};
        for (int k = 0; k < 4; k++) {
            xs[k] = at.x() - ux * corners[k][0] + rx * corners[k][1];
            ys[k] = at.y() - uy * corners[k][0] + ry * corners[k][1];
        }
        gc.fillPolygon(xs, ys, 4);
    }

    // La calle sigue hasta el borde y se funde con el fondo; una flecha doble indica entrada y salida.
    private void paintGateways(GraphicsContext gc) {
        for (Intersection g : gateways) {
            Road out = graph.outgoing(g).get(0);
            double length = out.length();
            double ux = (out.to().x() - g.x()) / length;
            double uy = (out.to().y() - g.y()) / length;
            double reach = LANE_WIDTH + SIDEWALK;
            var fade = new LinearGradient(g.x() + ux * GATEWAY_FADE, g.y() + uy * GATEWAY_FADE, g.x(), g.y(),
                    false, CycleMethod.NO_CYCLE, new Stop(0, BACKGROUND_CLEAR), new Stop(1, BACKGROUND));
            gc.setFill(fade);
            double[] xs = {g.x() - uy * reach, g.x() + uy * reach, g.x() + uy * reach + ux * GATEWAY_FADE,
                g.x() - uy * reach + ux * GATEWAY_FADE};
            double[] ys = {g.y() + ux * reach, g.y() - ux * reach, g.y() - ux * reach + uy * GATEWAY_FADE,
                g.y() + ux * reach + uy * GATEWAY_FADE};
            gc.fillPolygon(xs, ys, 4);

            double cx = g.x() + ux * ARROW_DISTANCE;
            double cy = g.y() + uy * ARROW_DISTANCE;
            gc.setStroke(ARROW);
            gc.setLineWidth(ARROW_WIDTH);
            gc.setLineCap(StrokeLineCap.ROUND);
            double ax = cx - ux * ARROW_HALF_LENGTH;
            double ay = cy - uy * ARROW_HALF_LENGTH;
            double bx = cx + ux * ARROW_HALF_LENGTH;
            double by = cy + uy * ARROW_HALF_LENGTH;
            gc.strokeLine(ax, ay, bx, by);
            arrowHead(gc, ax, ay, -ux, -uy);
            arrowHead(gc, bx, by, ux, uy);
            gc.setLineCap(StrokeLineCap.BUTT);
        }
    }

    private static void arrowHead(GraphicsContext gc, double tipX, double tipY, double ux, double uy) {
        double nx = -uy;
        double ny = ux;
        gc.strokeLine(tipX, tipY, tipX - ux * ARROW_HEAD + nx * ARROW_HEAD, tipY - uy * ARROW_HEAD + ny * ARROW_HEAD);
        gc.strokeLine(tipX, tipY, tipX - ux * ARROW_HEAD - nx * ARROW_HEAD, tipY - uy * ARROW_HEAD - ny * ARROW_HEAD);
    }

    // "P" blanca sobre azul junto a la entrada de cada playa, siempre derecha aunque la playa esté girada.
    private void paintSigns(GraphicsContext gc) {
        gc.setFont(SIGN_FONT);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);
        for (Lot lot : lots) {
            double nx = -lot.dy();
            double ny = lot.dx();
            double offset = lot.width() / 2 + SIGN_GAP + SIGN_SIZE / 2;
            double cx = lot.parking().x() + lot.dx() * SIGN_SIZE / 2 + nx * offset;
            double cy = lot.parking().y() + lot.dy() * SIGN_SIZE / 2 + ny * offset;
            gc.setFill(PARKING_SIGN);
            gc.fillRoundRect(cx - SIGN_SIZE / 2, cy - SIGN_SIZE / 2, SIGN_SIZE, SIGN_SIZE, 2 * SIGN_RADIUS,
                    2 * SIGN_RADIUS);
            gc.setFill(WHITE_PAINT);
            gc.fillText("P", cx, cy);
        }
    }

    // ---------------------------------------------------------------- auxiliares

    // La doble línea amarilla se corta antes del paso de cebra; en un JUNCTION o un gateway sigue.
    private static double centerTrim(Intersection end, double half) {
        return end.kind() == NodeKind.INTERSECTION ? half + SIDEWALK + STOP_LINE_GAP + STOP_LINE_WIDTH : 0;
    }

    private static double edgeTrim(Intersection end, double half) {
        return end.kind() == NodeKind.INTERSECTION ? half + SIDEWALK : 0;
    }

    private List<Road> incomingStreets(Intersection at) {
        return graph.edges().stream()
                .filter(r -> r.to().equals(at) && !r.from().isParking())
                .toList();
    }

    private Road reverseOf(Road road) {
        for (Road candidate : graph.outgoing(road.to())) {
            if (candidate.to().equals(road.from())) {
                return candidate;
            }
        }
        return null;
    }

    // Esquinas en orden: arriba-izquierda, arriba-derecha, abajo-derecha, abajo-izquierda.
    private static void roundedRect(GraphicsContext gc, double x, double y, double w, double h, boolean[] rounded) {
        double r = Math.min(CORNER_RADIUS, Math.min(w, h) / 2);
        double tl = rounded[0] ? r : 0;
        double tr = rounded[1] ? r : 0;
        double br = rounded[2] ? r : 0;
        double bl = rounded[3] ? r : 0;
        gc.beginPath();
        gc.moveTo(x + tl, y);
        gc.lineTo(x + w - tr, y);
        gc.arcTo(x + w, y, x + w, y + tr, tr);
        gc.lineTo(x + w, y + h - br);
        gc.arcTo(x + w, y + h, x + w - br, y + h, br);
        gc.lineTo(x + bl, y + h);
        gc.arcTo(x, y + h, x, y + h - bl, bl);
        gc.lineTo(x, y + tl);
        gc.arcTo(x, y, x + tl, y, tl);
        gc.closePath();
    }

    private static double min(double[] values) {
        double m = Double.POSITIVE_INFINITY;
        for (double v : values) {
            m = Math.min(m, v);
        }
        return m;
    }

    private static double max(double[] values) {
        double m = Double.NEGATIVE_INFINITY;
        for (double v : values) {
            m = Math.max(m, v);
        }
        return m;
    }
}
