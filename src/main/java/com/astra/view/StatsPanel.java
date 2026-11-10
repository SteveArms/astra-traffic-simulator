package com.astra.view;

import com.astra.core.StatsPoint;
import com.astra.core.StatsView;
import com.astra.core.WorldSnapshot;
import com.astra.model.VehicleKind;
import com.astra.traffic.LightMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.HPos;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Métricas del último snapshot y dos gráficas con un eje cada una (espera y throughput no comparten escala).
 *
 * @implNote Called from the FX Application Thread.
 */
public final class StatsPanel extends VBox {

    private static final Duration REFRESH = Duration.millis(500);
    private static final int SECONDS_PER_MINUTE = 60;

    private final Supplier<WorldSnapshot> snapshots;
    private final GridPane grid = new GridPane();
    private final Label cars = value();
    private final Label buses = value();
    private final Label ambulances = value();
    private final Label arrived = value();
    private final Label avgWait = value();
    private final Label avgTrip = value();
    private final Label throughput = value();
    private final Label queue = value();
    private final Label lightMode = value();
    private final Label tick = value();
    private final Label simTime = value();
    private final XYChart.Series<Number, Number> waitSeries = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> throughputSeries = new XYChart.Series<>();
    private final Timeline refresh;

    public StatsPanel(Supplier<WorldSnapshot> snapshots) {
        this.snapshots = Objects.requireNonNull(snapshots, "snapshots");
        getStyleClass().addAll("panel", "stats-panel");

        var keys = new ColumnConstraints();
        var values = new ColumnConstraints();
        values.setHalignment(HPos.RIGHT);
        values.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(keys, values);
        grid.getStyleClass().add("stats-grid");
        addRow("Autos", cars);
        addRow("Buses", buses);
        addRow("Ambulancias", ambulances);
        addRow("Llegados", arrived);
        addRow("Espera promedio", avgWait);
        addRow("Viaje promedio", avgTrip);
        addRow("Throughput", throughput);
        addRow("Cola actual / máx.", queue);
        addRow("Semáforos", lightMode);
        addRow("Tick", tick);
        addRow("Tiempo simulado", simTime);

        LineChart<Number, Number> waitChart = chart("Espera promedio (s)", waitSeries, "wait-chart");
        LineChart<Number, Number> throughputChart = chart("Throughput (veh/min)", throughputSeries,
                "throughput-chart");
        VBox charts = section("Evolución", waitChart, throughputChart);
        VBox.setVgrow(charts, Priority.ALWAYS);
        getChildren().addAll(section("Estadísticas", grid), charts);

        refresh = new Timeline(new KeyFrame(REFRESH, e -> update()));
        refresh.setCycleCount(Animation.INDEFINITE);
    }

    public void start() {
        update();
        refresh.play();
    }

    public void stop() {
        refresh.stop();
    }

    private void update() {
        WorldSnapshot snapshot = snapshots.get();
        StatsView stats = snapshot.stats();
        cars.setText(Integer.toString(snapshot.activeCount(VehicleKind.CAR)));
        buses.setText(Integer.toString(snapshot.activeCount(VehicleKind.BUS)));
        ambulances.setText(Integer.toString(snapshot.activeCount(VehicleKind.AMBULANCE)));
        arrived.setText(Long.toString(stats.totalArrived()));
        avgWait.setText("%.1f s".formatted(stats.avgWaitSeconds()));
        avgTrip.setText("%.1f s".formatted(stats.avgTripSeconds()));
        throughput.setText("%.1f veh/min".formatted(stats.throughputPerMin()));
        queue.setText("%d / %d".formatted(stats.currentQueue(), stats.maxQueue()));
        lightMode.setText(snapshot.lightMode() == LightMode.SMART ? "Inteligente" : "Fijo");
        tick.setText(Long.toString(snapshot.tick()));
        long seconds = (long) snapshot.simTimeSeconds();
        simTime.setText("%02d:%02d".formatted(seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE));

        waitSeries.getData().setAll(points(stats.series(), StatsPoint::avgWaitSeconds));
        throughputSeries.getData().setAll(points(stats.series(), StatsPoint::throughputPerMin));
    }

    private static List<XYChart.Data<Number, Number>> points(List<StatsPoint> series,
            ToDoubleFunction<StatsPoint> metric) {
        List<XYChart.Data<Number, Number>> data = new ArrayList<>(series.size());
        for (StatsPoint point : series) {
            data.add(new XYChart.Data<>(point.simTimeSeconds(), metric.applyAsDouble(point)));
        }
        return data;
    }

    private void addRow(String key, Label valueLabel) {
        Label keyLabel = new Label(key);
        keyLabel.getStyleClass().add("stats-key");
        keyLabel.setLabelFor(valueLabel);
        grid.addRow(grid.getRowCount(), keyLabel, valueLabel);
    }

    private static Label value() {
        Label label = new Label("—");
        label.getStyleClass().add("value");
        return label;
    }

    private static LineChart<Number, Number> chart(String title, XYChart.Series<Number, Number> series,
            String styleClass) {
        var x = new NumberAxis();
        x.setForceZeroInRange(false);
        x.setLabel("Tiempo simulado (s)");
        x.setMinorTickVisible(false);
        var y = new NumberAxis();
        y.setForceZeroInRange(true);
        y.setMinorTickVisible(false);
        var chart = new LineChart<>(x, y);
        chart.setTitle(title);
        chart.setAnimated(false);
        chart.setCreateSymbols(false);
        // Una serie por gráfica: el título la nombra, sobra la leyenda.
        chart.setLegendVisible(false);
        chart.getData().add(series);
        chart.getStyleClass().add(styleClass);
        chart.setMinHeight(0);
        VBox.setVgrow(chart, Priority.ALWAYS);
        return chart;
    }

    private static VBox section(String title, Node... content) {
        Label heading = new Label(title);
        heading.getStyleClass().add("section-title");
        VBox box = new VBox(heading);
        box.getChildren().addAll(content);
        box.getStyleClass().add("section");
        return box;
    }
}
