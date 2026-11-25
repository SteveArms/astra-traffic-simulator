package com.astra.view;

import javafx.scene.canvas.GraphicsContext;

/**
 * Escala uniforme + traslación que encaja el mundo ({@code worldWidth x worldHeight}) en el canvas con un margen,
 * centrado y sin deformarlo. Convierte en los dos sentidos para dibujar y para interpretar clics.
 *
 * @implNote Called from the FX Application Thread.
 */
final class ViewTransform {

    private double scale = 1;
    private double translateX;
    private double translateY;

    /** Recalcula la transformación; con un canvas sin tamaño útil deja la identidad. */
    void fit(double canvasWidth, double canvasHeight, double worldWidth, double worldHeight, double margin) {
        double usableWidth = canvasWidth - 2 * margin;
        double usableHeight = canvasHeight - 2 * margin;
        if (usableWidth <= 0 || usableHeight <= 0 || worldWidth <= 0 || worldHeight <= 0) {
            scale = 1;
            translateX = 0;
            translateY = 0;
            return;
        }
        scale = Math.min(usableWidth / worldWidth, usableHeight / worldHeight);
        translateX = (canvasWidth - worldWidth * scale) / 2;
        translateY = (canvasHeight - worldHeight * scale) / 2;
    }

    double scale() {
        return scale;
    }

    double toScreenX(double worldX) {
        return translateX + worldX * scale;
    }

    double toScreenY(double worldY) {
        return translateY + worldY * scale;
    }

    double toWorldX(double screenX) {
        return (screenX - translateX) / scale;
    }

    double toWorldY(double screenY) {
        return (screenY - translateY) / scale;
    }

    /** Lo que se dibuje después usa coordenadas del mundo. */
    void apply(GraphicsContext gc) {
        gc.translate(translateX, translateY);
        gc.scale(scale, scale);
    }
}
