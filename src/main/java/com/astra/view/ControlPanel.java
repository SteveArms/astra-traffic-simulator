package com.astra.view;

import com.astra.command.CommandBus;
import com.astra.command.Commands;
import com.astra.core.SimulationControl;
import com.astra.core.WorldSnapshot;
import com.astra.model.VehicleKind;
import com.astra.routing.RouteStrategies;
import com.astra.traffic.Axis;
import com.astra.traffic.LightMode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Slider;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import javafx.util.StringConverter;

/**
 * Controles de la simulación. Nunca toca el mundo: cada acción es un {@code Command} enviado al bus.
 *
 * @implNote Called from the FX Application Thread.
 */
public final class ControlPanel extends VBox {

    private static final Duration REFRESH = Duration.millis(100);
    private static final Duration SPEED_DEBOUNCE = Duration.millis(200);
    private static final int BATCH_SIZE = 10;
    // El slider trabaja en log2 de la velocidad: marcas equidistantes en 0.25, 0.5, 1, 2 y 4.
    private static final double MIN_LOG2 = log2(SimulationControl.MIN_SPEED);
    private static final double MAX_LOG2 = log2(SimulationControl.MAX_SPEED);
    private static final double SPEED_STEP = 2;

    private final CommandBus bus;
    private final Supplier<WorldSnapshot> snapshots;
    private final Button pauseButton = new Button("Pausar");
    private final Button stepButton = new Button("Paso");
    private final Slider speedSlider = new Slider(MIN_LOG2, MAX_LOG2, 0);
    private final Label speedLabel = new Label();
    private final ChoiceBox<VehicleKind> kindChoice = new ChoiceBox<>();
    private final ChoiceBox<String> strategyChoice = new ChoiceBox<>();
    private final ToggleGroup modeGroup = new ToggleGroup();
    private final ToggleButton fixedToggle = new ToggleButton("Fijo");
    private final ToggleButton smartToggle = new ToggleButton("Inteligente");
    private final ChoiceBox<String> intersectionChoice = new ChoiceBox<>();
    private final ListView<String> history = new ListView<>();
    private final PauseTransition speedDebounce = new PauseTransition(SPEED_DEBOUNCE);
    private final Timeline refresh;

    private Consumer<String> onIntersectionChosen = id -> { };
    private List<String> shownHistory = List.of();
    // Evita reenviar comandos cuando el panel se sincroniza con el snapshot.
    private boolean syncing;

    public ControlPanel(CommandBus bus, Supplier<WorldSnapshot> snapshots, List<String> intersectionIds) {
        this.bus = Objects.requireNonNull(bus, "bus");
        this.snapshots = Objects.requireNonNull(snapshots, "snapshots");
        getStyleClass().addAll("panel", "control-panel");

        Node historyBox = historySection();
        VBox.setVgrow(historyBox, Priority.ALWAYS);
        getChildren().addAll(simulationSection(), vehiclesSection(), lightsSection(List.copyOf(intersectionIds)),
                historyBox);

        refresh = new Timeline(new KeyFrame(REFRESH, e -> sync()));
        refresh.setCycleCount(Animation.INDEFINITE);
    }

    public void start() {
        sync();
        refresh.play();
    }

    public void stop() {
        refresh.stop();
        speedDebounce.stop();
    }

    public void setOnIntersectionChosen(Consumer<String> listener) {
        onIntersectionChosen = Objects.requireNonNull(listener, "listener");
    }

    /** Selección hecha en el mapa; no vuelve a notificar. */
    public void selectIntersection(String id) {
        if (id != null && intersectionChoice.getItems().contains(id) && !id.equals(intersectionChoice.getValue())) {
            syncing = true;
            intersectionChoice.setValue(id);
            syncing = false;
        }
    }

    /** Espacio pausa o reanuda, S avanza un paso, + y − duplican o dividen la velocidad. */
    public void installShortcuts(Scene scene) {
        // KEY_PRESSED como handler (no filtro): un botón o un ChoiceBox con foco conserva su Espacio nativo.
        scene.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.SPACE) {
                bus.submit(Commands.togglePause());
                e.consume();
            }
        });
        // KEY_TYPED da el carácter real, sin depender de la distribución del teclado.
        scene.addEventHandler(KeyEvent.KEY_TYPED, e -> {
            switch (e.getCharacter()) {
                case "s", "S" -> {
                    if (snapshots.get().paused()) {
                        bus.submit(Commands.step());
                    }
                }
                case "+" -> changeSpeedBy(SPEED_STEP);
                case "-", "−" -> changeSpeedBy(1 / SPEED_STEP);
                default -> {
                    return;
                }
            }
            e.consume();
        });
    }

    private Node simulationSection() {
        pauseButton.getStyleClass().add("primary");
        pauseButton.setTooltip(new Tooltip("Espacio"));
        pauseButton.setOnAction(e -> bus.submit(Commands.togglePause()));
        stepButton.setTooltip(new Tooltip("Avanza un tick con la simulación en pausa (S)"));
        stepButton.setOnAction(e -> bus.submit(Commands.step()));
        stepButton.setDisable(true);
        HBox buttons = row(pauseButton, stepButton);

        speedSlider.setMajorTickUnit(1);
        speedSlider.setMinorTickCount(0);
        speedSlider.setShowTickMarks(true);
        speedSlider.setShowTickLabels(true);
        speedSlider.setLabelFormatter(new SpeedLabels());
        speedSlider.setAccessibleText("Velocidad de la simulación");
        speedSlider.setTooltip(new Tooltip("+ y − duplican o dividen la velocidad"));
        speedSlider.valueProperty().addListener((obs, old, now) -> {
            speedLabel.setText(formatSpeed(toSpeed(now.doubleValue())));
            if (!syncing && !speedSlider.isValueChanging()) {
                speedDebounce.playFromStart();
            }
        });
        speedSlider.valueChangingProperty().addListener((obs, was, changing) -> {
            if (!changing) {
                speedDebounce.stop();
                submitSpeed();
            }
        });
        speedDebounce.setOnFinished(e -> submitSpeed());
        speedLabel.setText(formatSpeed(1));
        speedLabel.getStyleClass().add("value");
        speedLabel.setLabelFor(speedSlider);

        HBox speedHeader = row(new Label("Velocidad"), spacer(), speedLabel);
        return section("Simulación", buttons, speedHeader, speedSlider);
    }

    private Node vehiclesSection() {
        kindChoice.getItems().setAll(VehicleKind.values());
        kindChoice.setValue(VehicleKind.CAR);
        kindChoice.setConverter(new KindNames());
        strategyChoice.getItems().setAll(RouteStrategies.NAMES);
        strategyChoice.setValue(RouteStrategies.NAMES.get(0));
        strategyChoice.setConverter(new StrategyNames());

        Button spawn = new Button("Generar");
        spawn.setOnAction(e -> spawn(1));
        Button spawnBatch = new Button("Generar ×10");
        spawnBatch.setOnAction(e -> spawn(BATCH_SIZE));

        return section("Vehículos", labeled("Tipo", kindChoice), labeled("Ruta", strategyChoice),
                row(spawn, spawnBatch));
    }

    private Node lightsSection(List<String> intersectionIds) {
        fixedToggle.setToggleGroup(modeGroup);
        smartToggle.setToggleGroup(modeGroup);
        fixedToggle.setUserData(LightMode.FIXED);
        smartToggle.setUserData(LightMode.SMART);
        fixedToggle.setTooltip(new Tooltip("Ciclo de tiempos fijos"));
        smartToggle.setTooltip(new Tooltip("Alarga el verde del eje con más vehículos esperando"));
        modeGroup.selectToggle(fixedToggle);
        modeGroup.selectedToggleProperty().addListener((obs, old, now) -> onModeToggled(old, now));

        intersectionChoice.getItems().setAll(intersectionIds);
        if (!intersectionIds.isEmpty()) {
            intersectionChoice.setValue(intersectionIds.get(0));
        }
        intersectionChoice.valueProperty().addListener((obs, old, now) -> {
            if (!syncing && now != null) {
                onIntersectionChosen.accept(now);
            }
        });

        Button greenNs = new Button("Verde NS");
        greenNs.setOnAction(e -> forceGreen(Axis.NS));
        Button greenEw = new Button("Verde EW");
        greenEw.setOnAction(e -> forceGreen(Axis.EW));
        greenNs.disableProperty().bind(intersectionChoice.valueProperty().isNull());
        greenEw.disableProperty().bind(intersectionChoice.valueProperty().isNull());

        return section("Semáforos", row(fixedToggle, smartToggle), labeled("Intersección", intersectionChoice),
                row(greenNs, greenEw));
    }

    private Node historySection() {
        history.setPlaceholder(new Label("Aún no hay comandos"));
        history.setFocusTraversable(false);
        VBox.setVgrow(history, Priority.ALWAYS);
        return section("Historial", history);
    }

    private void sync() {
        WorldSnapshot snapshot = snapshots.get();
        syncing = true;
        try {
            pauseButton.setText(snapshot.paused() ? "Reanudar" : "Pausar");
            stepButton.setDisable(!snapshot.paused());
            if (!speedSlider.isValueChanging() && speedDebounce.getStatus() != Animation.Status.RUNNING) {
                speedSlider.setValue(log2(snapshot.speedFactor()));
            }
            Toggle wanted = snapshot.lightMode() == LightMode.SMART ? smartToggle : fixedToggle;
            if (modeGroup.getSelectedToggle() != wanted) {
                modeGroup.selectToggle(wanted);
            }
        } finally {
            syncing = false;
        }
        List<String> recent = bus.recentHistory();
        if (!recent.equals(shownHistory)) {
            shownHistory = recent;
            List<String> newestFirst = new ArrayList<>(recent);
            Collections.reverse(newestFirst);
            history.getItems().setAll(newestFirst);
        }
    }

    private void onModeToggled(Toggle old, Toggle now) {
        if (now == null) {
            // Un ToggleGroup deja deseleccionar con un segundo clic; siempre hay un modo activo.
            modeGroup.selectToggle(old);
            return;
        }
        if (!syncing && now != old) {
            bus.submit(Commands.lightMode((LightMode) now.getUserData()));
        }
    }

    private void changeSpeedBy(double factor) {
        double target = Math.clamp(snapshots.get().speedFactor() * factor, SimulationControl.MIN_SPEED,
                SimulationControl.MAX_SPEED);
        bus.submit(Commands.speed(target));
        syncing = true;
        speedSlider.setValue(log2(target));
        syncing = false;
    }

    private void submitSpeed() {
        bus.submit(Commands.speed(toSpeed(speedSlider.getValue())));
    }

    private void spawn(int count) {
        for (int i = 0; i < count; i++) {
            bus.submit(Commands.spawn(kindChoice.getValue(), strategyChoice.getValue()));
        }
    }

    private void forceGreen(Axis axis) {
        String id = intersectionChoice.getValue();
        if (id != null) {
            bus.submit(Commands.forceLight(id, axis));
        }
    }

    private static VBox section(String title, Node... content) {
        Label heading = new Label(title);
        heading.getStyleClass().add("section-title");
        VBox box = new VBox(heading);
        box.getChildren().addAll(content);
        box.getStyleClass().add("section");
        return box;
    }

    private static HBox row(Node... nodes) {
        HBox box = new HBox(nodes);
        box.getStyleClass().add("row");
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private static HBox labeled(String text, ChoiceBox<?> control) {
        Label label = new Label(text);
        label.setLabelFor(control);
        label.getStyleClass().add("field-label");
        control.setAccessibleText(text);
        control.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(control, Priority.ALWAYS);
        return row(label, control);
    }

    private static Region spacer() {
        Region region = new Region();
        HBox.setHgrow(region, Priority.ALWAYS);
        return region;
    }

    private static double log2(double value) {
        return Math.log(value) / Math.log(2);
    }

    private static double toSpeed(double log2) {
        return Math.pow(2, log2);
    }

    private static String formatSpeed(double speed) {
        return "×%.2f".formatted(speed);
    }

    private static final class SpeedLabels extends StringConverter<Double> {
        @Override
        public String toString(Double log2) {
            return "×" + BigDecimal.valueOf(toSpeed(log2)).stripTrailingZeros().toPlainString();
        }

        @Override
        public Double fromString(String text) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class KindNames extends StringConverter<VehicleKind> {
        @Override
        public String toString(VehicleKind kind) {
            return kind == null ? "" : switch (kind) {
                case CAR -> "Auto";
                case BUS -> "Bus";
                case AMBULANCE -> "Ambulancia";
            };
        }

        @Override
        public VehicleKind fromString(String text) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class StrategyNames extends StringConverter<String> {
        @Override
        public String toString(String name) {
            return name == null ? "" : switch (name) {
                case "SHORTEST" -> "Más corta";
                case "LEAST_CONGESTED" -> "Menos congestionada";
                case "RANDOM" -> "Aleatoria";
                default -> name;
            };
        }

        @Override
        public String fromString(String text) {
            throw new UnsupportedOperationException();
        }
    }
}
