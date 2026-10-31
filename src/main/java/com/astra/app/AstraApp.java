package com.astra.app;

import com.astra.core.SimConfig;
import com.astra.util.MapLoader;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

public final class AstraApp extends Application {

    private static final String TITLE = "ASTRA — Traffic Simulator";
    private static final double WINDOW_WIDTH = 1100;
    private static final double WINDOW_HEIGHT = 700;

    @Override
    public void start(Stage stage) {
        SimConfig config = SimConfig.load();
        MapLoader.LoadedMap map = MapLoader.load(config.mapFile());
        String summary = "Mapa: %s — %d intersecciones, %d vías"
                .formatted(map.name(), map.graph().vertexCount(), map.graph().edgeCount());

        var root = new BorderPane();
        root.setCenter(new Label(summary));

        stage.setTitle(TITLE);
        stage.setScene(new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT));
        stage.show();
    }

    @Override
    public void stop() {
        // TODO: detener SimulationEngine y hacer shutdownNow() + awaitTermination() de los executors.
    }

    public static void main(String[] args) {
        launch(args);
    }
}
