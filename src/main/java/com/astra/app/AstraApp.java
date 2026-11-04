package com.astra.app;

import com.astra.core.IntersectionZones;
import com.astra.core.SimConfig;
import com.astra.core.SimulationEngine;
import com.astra.core.VehicleSpawner;
import com.astra.core.WorldState;
import com.astra.traffic.LightController;
import com.astra.util.MapLoader;
import com.astra.view.CityRenderer;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

public final class AstraApp extends Application {

    private static final String TITLE = "ASTRA — Traffic Simulator · %s";
    private static final double WINDOW_WIDTH = 1100;
    private static final double WINDOW_HEIGHT = 700;

    private LightController lights;
    private SimulationEngine engine;
    private CityRenderer renderer;

    @Override
    public void start(Stage stage) {
        SimConfig config = SimConfig.load();
        MapLoader.LoadedMap map = MapLoader.load(config.mapFile());
        lights = new LightController(map.graph(), config);
        var world = new WorldState(map.graph(), config, lights, new IntersectionZones(map.graph(), config));
        engine = new SimulationEngine(config, world, new VehicleSpawner(world, config));
        renderer = new CityRenderer(map.graph(), engine::latestSnapshot);

        var root = new BorderPane();
        root.setCenter(renderer);
        stage.setTitle(TITLE.formatted(map.name()));
        stage.setScene(new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT));
        stage.show();

        lights.start();
        engine.start();
        renderer.start();
    }

    @Override
    public void stop() {
        if (renderer != null) {
            renderer.stop();
        }
        if (engine != null) {
            engine.stop();
        }
        if (lights != null) {
            lights.stop();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
