package com.astra.app;

import com.astra.command.CommandBus;
import com.astra.core.IntersectionZones;
import com.astra.core.PauseListener;
import com.astra.core.SimConfig;
import com.astra.core.SimulationControl;
import com.astra.core.SimulationEngine;
import com.astra.core.VehicleSpawner;
import com.astra.core.WorldState;
import com.astra.graph.Intersection;
import com.astra.traffic.LightController;
import com.astra.traffic.TrafficLight;
import com.astra.util.MapLoader;
import com.astra.view.CityRenderer;
import com.astra.view.ControlPanel;
import com.astra.view.StatsPanel;
import java.util.List;
import java.util.Objects;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

public final class AstraApp extends Application {

    private static final String TITLE = "ASTRA — Traffic Simulator · %s";
    private static final String STYLESHEET = "/com/astra/css/astra.css";
    private static final double WINDOW_WIDTH = 1280;
    private static final double WINDOW_HEIGHT = 760;
    private static final double CONTROL_PANEL_WIDTH = 260;
    private static final double STATS_PANEL_WIDTH = 300;

    private LightController lights;
    private SimulationEngine engine;
    private CityRenderer renderer;
    private ControlPanel controlPanel;
    private StatsPanel statsPanel;

    @Override
    public void start(Stage stage) {
        SimConfig config = SimConfig.load();
        MapLoader.LoadedMap map = MapLoader.load(config.mapFile());
        var control = new SimulationControl();
        lights = new LightController(map.graph(), config, config, control::speedFactor);
        control.pauseGate().addListener(PauseListener.of(lights::pause, lights::resume));
        var world = new WorldState(map.graph(), config, lights, new IntersectionZones(map.graph(), config));
        var bus = new CommandBus();
        engine = new SimulationEngine(config, world, new VehicleSpawner(world, config), bus, control);

        List<String> lightIds = lights.lights().stream()
                .map(TrafficLight::intersection)
                .map(Intersection::id)
                .sorted()
                .toList();
        renderer = new CityRenderer(map.graph(), engine::latestSnapshot, bus);
        controlPanel = new ControlPanel(bus, engine::latestSnapshot, lightIds);
        statsPanel = new StatsPanel(engine::latestSnapshot);
        renderer.setOnIntersectionSelected(controlPanel::selectIntersection);
        controlPanel.setOnIntersectionChosen(renderer::select);
        if (!lightIds.isEmpty()) {
            renderer.select(lightIds.get(0));
        }
        controlPanel.setPrefWidth(CONTROL_PANEL_WIDTH);
        controlPanel.setMinWidth(CONTROL_PANEL_WIDTH);
        statsPanel.setPrefWidth(STATS_PANEL_WIDTH);
        statsPanel.setMinWidth(STATS_PANEL_WIDTH);

        var root = new BorderPane(renderer, null, statsPanel, null, controlPanel);
        var scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT);
        scene.getStylesheets().add(
                Objects.requireNonNull(AstraApp.class.getResource(STYLESHEET), STYLESHEET).toExternalForm());
        controlPanel.installShortcuts(scene);
        stage.setTitle(TITLE.formatted(map.name()));
        stage.setScene(scene);
        stage.show();

        lights.start();
        engine.start();
        renderer.start();
        controlPanel.start();
        statsPanel.start();
    }

    @Override
    public void stop() {
        if (statsPanel != null) {
            statsPanel.stop();
        }
        if (controlPanel != null) {
            controlPanel.stop();
        }
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
