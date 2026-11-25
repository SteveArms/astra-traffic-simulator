package com.astra.view;

import com.astra.graph.Intersection;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Geometría de una playa en coordenadas del mundo, sin JavaFX: la playa arranca en su nodo y se extiende hacia
 * adentro de la manzana, con un pasillo central y una fila de cajones a cada lado. La usan la capa estática (para
 * pintar los cajones) y la dinámica (para ubicar cada auto estacionado), así que las dos coinciden siempre.
 *
 * <p>Sistema local: x desde la entrada hacia adentro, y a lo ancho (a la derecha de quien entra). El cajón {@code i}
 * de la playa es la fila de y negativa para {@code i < sideA()} y la de y positiva para el resto, ambas desde la
 * entrada: el mismo orden que los índices de {@code ResourcePool}.
 */
final class LotGeometry {

    static final double STALL_WIDTH = 10;
    static final double STALL_DEPTH = 16;
    static final double AISLE = 14;
    static final double LOT_PAD = 4;

    /** Centro de un cajón y rumbo de un auto estacionado en él (con el frente hacia el pasillo). */
    record Stall(double x, double y, double headingRad) {
    }

    private final Intersection parking;
    private final Intersection junction;
    private final double dx;
    private final double dy;
    private final double depth;
    private final double width;
    private final int capacity;

    LotGeometry(Intersection parking, Intersection junction) {
        this.parking = Objects.requireNonNull(parking, "parking");
        this.junction = Objects.requireNonNull(junction, "junction");
        double length = parking.distanceTo(junction);
        this.dx = (parking.x() - junction.x()) / length;
        this.dy = (parking.y() - junction.y()) / length;
        this.capacity = parking.capacity();
        this.depth = sideA() * STALL_WIDTH + 2 * LOT_PAD;
        this.width = 2 * STALL_DEPTH + AISLE;
    }

    String id() {
        return parking.id();
    }

    Intersection parking() {
        return parking;
    }

    Intersection junction() {
        return junction;
    }

    /** Dirección unitaria de la entrada hacia adentro de la manzana. */
    double dx() {
        return dx;
    }

    double dy() {
        return dy;
    }

    double depth() {
        return depth;
    }

    double width() {
        return width;
    }

    int capacity() {
        return capacity;
    }

    /** Cajones de la fila de y negativa (la mitad redondeada hacia arriba). */
    int sideA() {
        return (capacity + 1) / 2;
    }

    int sideB() {
        return capacity / 2;
    }

    double angleDegrees() {
        return Math.toDegrees(Math.atan2(dy, dx));
    }

    /** Los cajones en el orden de sus índices. */
    List<Stall> stalls() {
        List<Stall> stalls = new ArrayList<>(capacity);
        double centerY = width / 2 - STALL_DEPTH / 2;
        double base = Math.atan2(dy, dx);
        for (int i = 0; i < capacity; i++) {
            boolean first = i < sideA();
            int k = first ? i : i - sideA();
            double localX = LOT_PAD + (k + 0.5) * STALL_WIDTH;
            double localY = first ? -centerY : centerY;
            // El frente mira al pasillo: +90° en la fila de y negativa, -90° en la otra.
            double heading = base + (first ? Math.PI / 2 : -Math.PI / 2);
            stalls.add(new Stall(worldX(localX, localY), worldY(localX, localY), heading));
        }
        return stalls;
    }

    /** Los 4 vértices del rectángulo de la playa, en x y en y. */
    double[][] outline() {
        double half = width / 2;
        return new double[][] {
            {worldX(0, -half), worldX(depth, -half), worldX(depth, half), worldX(0, half)},
            {worldY(0, -half), worldY(depth, -half), worldY(depth, half), worldY(0, half)},
        };
    }

    double worldX(double localX, double localY) {
        return parking.x() + dx * localX - dy * localY;
    }

    double worldY(double localX, double localY) {
        return parking.y() + dy * localX + dx * localY;
    }
}
