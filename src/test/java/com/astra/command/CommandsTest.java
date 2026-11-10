package com.astra.command;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.astra.core.IntersectionZones;
import com.astra.core.SimConfig;
import com.astra.core.SimulationEngine;
import com.astra.core.VehicleSpawner;
import com.astra.core.WorldSnapshot;
import com.astra.core.WorldState;
import com.astra.graph.RoadGraph;
import com.astra.model.VehicleKind;
import com.astra.traffic.Axis;
import com.astra.traffic.LightColor;
import com.astra.traffic.LightController;
import com.astra.traffic.LightMode;
import com.astra.util.MapLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Headless: el motor avanza con step(), que corre cada tick en el hilo astra-engine. */
@Timeout(10)
class CommandsTest {

    private static final double DELTA = 1e-9;
    private static final double DT = 0.05;
    private static final String LIGHT_ID = "I11";

    private LightController lights;
    private SimulationEngine engine;
    private CommandBus bus;

    @BeforeEach
    void setUp() {
        var props = new Properties();
        // Sin generación automática: solo existen los vehículos que piden los comandos.
        props.setProperty("spawn.intervalMillis", "3600000");
        var cfg = SimConfig.from(props);
        RoadGraph graph = MapLoader.load(cfg.mapFile()).graph();
        lights = new LightController(graph, cfg);
        var world = new WorldState(graph, cfg, lights, new IntersectionZones(graph, cfg));
        bus = new CommandBus();
        engine = new SimulationEngine(cfg, world, new VehicleSpawner(world, cfg), bus);
    }

    @AfterEach
    void tearDown() {
        engine.stop();
    }

    @Test
    void pauseResumeAndToggleChangeSnapshot() {
        assertPausedAfter(Commands.pause(), true);
        assertPausedAfter(Commands.togglePause(), false);
        assertPausedAfter(Commands.togglePause(), true);
        assertPausedAfter(Commands.resume(), false);
    }

    @Test
    void stepWhilePausedAdvancesExactlyThatManyTicks() {
        run(Commands.pause());
        double frozen = engine.latestSnapshot().simTimeSeconds();

        bus.submit(Commands.step(3));
        for (int i = 0; i < 5; i++) {
            engine.step();
        }

        assertTrue(engine.latestSnapshot().paused());
        assertEquals(frozen + 3 * DT, engine.latestSnapshot().simTimeSeconds(), DELTA);
    }

    @Test
    void stepWithoutPauseIsIgnored() {
        engine.step();
        double before = engine.latestSnapshot().simTimeSeconds();

        run(Commands.step(3));
        assertEquals(before + DT, engine.latestSnapshot().simTimeSeconds(), DELTA);
        engine.control().pauseGate().pause();
        engine.step();

        assertEquals(before + DT, engine.latestSnapshot().simTimeSeconds(), DELTA, "no steps left over");
        assertEquals(1, bus.ignoredCount());
        assertTrue(lastHistory().endsWith("Step(3) (ignored: not paused)"), lastHistory());
    }

    @Test
    void stepRangeIsValidated() {
        assertThrows(IllegalArgumentException.class, () -> Commands.step(0));
        assertThrows(IllegalArgumentException.class, () -> Commands.step(StepCmd.MAX_STEPS + 1));
    }

    @Test
    void setSpeedIsReflectedInSnapshot() {
        run(Commands.speed(2.0));

        assertEquals(2.0, engine.latestSnapshot().speedFactor(), DELTA);
        assertThrows(IllegalArgumentException.class, () -> Commands.speed(5));
        assertThrows(IllegalArgumentException.class, () -> Commands.speed(Double.NaN));
    }

    @Test
    void spawnWithExplicitEndsCreatesRequestedKind() {
        run(Commands.spawn(VehicleKind.BUS, "shortest", "I00", "I22"));

        WorldSnapshot snapshot = engine.latestSnapshot();
        assertEquals(1, snapshot.vehicleCount());
        assertEquals(1, snapshot.activeCount(VehicleKind.BUS));
        assertEquals(VehicleKind.BUS, snapshot.vehicles().get(0).kind());
        assertEquals("SHORTEST", snapshot.vehicles().get(0).strategyName());
    }

    @Test
    void spawnWithRandomEndsUsesTheBorder() {
        run(Commands.spawn(VehicleKind.AMBULANCE, "RANDOM"));

        assertEquals(1, engine.latestSnapshot().activeCount(VehicleKind.AMBULANCE));
    }

    @Test
    void spawnWithUnknownIntersectionIsIgnored() {
        assertDoesNotThrow(() -> run(Commands.spawn(VehicleKind.CAR, "SHORTEST", "NOPE", null)));

        assertEquals(0, engine.latestSnapshot().vehicleCount());
        assertTrue(lastHistory().contains("ignored: unknown intersection NOPE"), lastHistory());
        assertThrows(IllegalArgumentException.class, () -> Commands.spawn(VehicleKind.CAR, "TELEPORT"));
        assertThrows(IllegalArgumentException.class, () -> Commands.spawn(VehicleKind.CAR, "SHORTEST", "I00", "I00"));
    }

    @Test
    void forceLightPutsRequestedAxisInGreen() {
        run(Commands.forceLight(LIGHT_ID, Axis.EW));
        assertLight(LightColor.RED, LightColor.GREEN);

        run(Commands.forceLight(LIGHT_ID, Axis.NS));
        assertLight(LightColor.GREEN, LightColor.RED);
    }

    @Test
    void forceLightWithInvalidIdIsIgnored() {
        // I00 existe pero es una esquina sin semáforo.
        assertDoesNotThrow(() -> run(Commands.forceLight("NOPE", Axis.EW)));
        assertDoesNotThrow(() -> run(Commands.forceLight("I00", Axis.EW)));

        assertEquals(0, bus.failedCount());
        assertEquals(2, bus.ignoredCount());
    }

    @Test
    void setLightModeUpdatesController() {
        run(Commands.lightMode(LightMode.SMART));

        assertEquals(LightMode.SMART, lights.mode());
    }

    @Test
    void allCommandsRunOnEngineThread() {
        List<Command> all = List.of(Commands.pause(), Commands.step(2), Commands.speed(0.5),
                Commands.spawn(VehicleKind.CAR, "LEAST_CONGESTED", "I00", "I02"),
                Commands.forceLight(LIGHT_ID, Axis.EW),
                Commands.lightMode(LightMode.SMART), Commands.togglePause(), Commands.resume());
        List<String> threads = new ArrayList<>();
        for (Command command : all) {
            bus.submit(engine -> {
                threads.add(Thread.currentThread().getName());
                command.execute(engine);
            });
        }

        engine.step();

        assertEquals(all.size(), threads.size());
        assertTrue(threads.stream().allMatch(SimulationEngine.THREAD_NAME::equals), threads.toString());
        assertEquals(0, bus.failedCount());
        assertFalse(engine.latestSnapshot().paused());
    }

    private void run(Command command) {
        assertTrue(bus.submit(command));
        engine.step();
    }

    private void assertPausedAfter(Command command, boolean paused) {
        run(command);
        assertEquals(paused, engine.latestSnapshot().paused(), command.describe());
    }

    private void assertLight(LightColor ns, LightColor ew) {
        var view = engine.latestSnapshot().lights().stream()
                .filter(l -> l.intersectionId().equals(LIGHT_ID))
                .findFirst()
                .orElseThrow();
        assertEquals(ns, view.ns());
        assertEquals(ew, view.ew());
    }

    private String lastHistory() {
        List<String> history = bus.recentHistory();
        return history.get(history.size() - 1);
    }
}
