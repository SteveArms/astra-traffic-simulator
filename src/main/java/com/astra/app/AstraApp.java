package com.astra.app;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

/**
 * Punto de entrada JavaFX de ASTRA.
 */
public final class AstraApp extends Application {

    private static final String TITLE = "ASTRA — Traffic Simulator";
    private static final double WINDOW_WIDTH = 1100;
    private static final double WINDOW_HEIGHT = 700;

    /**
     * @implNote Called from the JavaFX Application Thread.
     */
    @Override
    public void start(Stage stage) {
        var root = new BorderPane();
        root.setCenter(new Label("ASTRA iniciado"));

        stage.setTitle(TITLE);
        stage.setScene(new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT));
        stage.show();
    }

    /**
     * @implNote Called from the JavaFX Application Thread when the application exits.
     */
    @Override
    public void stop() {
        // TODO: detener SimulationEngine y hacer shutdownNow() + awaitTermination() de todos los executors
        //       (astra-engine, astra-worker-N, astra-lights-N).
    }

    public static void main(String[] args) {
        launch(args);
    }
}
