package com.astra.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.astra.command.Commands;
import com.astra.distributed.IntersectionMutex;
import com.astra.distributed.SimulatedNetwork;
import com.astra.graph.Intersection;
import com.astra.graph.Road;
import com.astra.graph.RoadGraph;
import com.astra.model.Vehicle;
import com.astra.traffic.LightController;
import com.astra.traffic.TestLights;
import com.astra.traffic.TrafficLight;
import com.astra.util.LogSettings;
import com.astra.util.SimLogger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Cruce con Ricart-Agrawala de punta a punta: headless con {@code step()}, pero con el hilo astra-net real. Usa la
 * malla de 6x4 de {@link EngineStressTest} (el mapa 3x3 no admite 150 vehículos) y latencias cortas, porque
 * {@code step()} avanza los ticks mucho más rápido que el reloj real.
 */
@Timeout(120)
class DistributedCrossingTest {

    private static final int MAX_VEHICLES = 150;
    private static final int TICKS = 3_000;
    private static final int TICKS_PER_LIGHT_PHASE = 160;
    private static final int COLUMNS = 6;
    private static final int ROWS = 4;
    private static final double SPACING = 160;
    private static final double SPEED_LIMIT = 14;

    @TempDir
    Path dir;

    private Path logFile;
    private SimulatedNetwork network;
    private WorldState world;
    private LightController lights;
    private SimulationEngine engine;

    @BeforeEach
    void setUp() {
        logFile = dir.resolve("astra.log");
        SimLogger.init(new Logs(logFile.toString()));
    }

    @AfterEach
    void tearDown() {
        if (engine != null) {
            engine.stop();
        }
        if (network != null) {
            network.stop();
        }
        SimLogger.shutdown();
    }

    @Test
    void ricartAgrawalaKeepsOneAccessPerBoxWithoutDeadlock() {
        build(MutexMode.RICART_AGRAWALA);
        long despawnedAtTwoThirds = 0;

        for (int tick = 1; tick <= TICKS; tick++) {
            step(tick);
            checkOneAccessPerBox(tick);
            if (tick == TICKS * 2 / 3) {
                despawnedAtTwoThirds = world.despawned();
            }
        }

        assertTrue(world.despawned() > 0, "throughput is zero");
        assertTrue(world.despawned() > despawnedAtTwoThirds, "no arrivals in the last third: deadlock?");
        assertTrue(network.sentCount() > 0);
    }

    @Test
    void switchingModesLiveLeavesNoVehicleStuck() {
        build(MutexMode.SEMAPHORE);
        int tick = 0;
        while (tick < 400) {
            step(++tick);
        }
        engine.commandBus().submit(Commands.mutexMode(MutexMode.RICART_AGRAWALA));
        while (tick < 1_400) {
            step(++tick);
            checkOneAccessPerBox(tick);
        }
        assertEquals(MutexMode.RICART_AGRAWALA, world.mutexMode());
        Map<String, Road> waiting = new HashMap<>();
        Map<String, Vehicle> byId = vehiclesById();
        for (String id : world.raQueuedVehicles()) {
            waiting.put(id, byId.get(id).currentRoad());
        }
        assertFalse(waiting.isEmpty(), "no vehicle was waiting on Ricart-Agrawala");

        engine.commandBus().submit(Commands.mutexMode(MutexMode.SEMAPHORE));
        step(++tick);
        assertEquals(MutexMode.SEMAPHORE, world.mutexMode());
        assertTrue(world.raQueuedVehicles().isEmpty());
        Set<String> crossed = new HashSet<>();
        for (int i = 0; i < 2_000 && crossed.size() < waiting.size(); i++) {
            step(++tick);
            Map<String, Vehicle> now = vehiclesById();
            waiting.forEach((id, road) -> {
                Vehicle vehicle = now.get(id);
                if (vehicle == null || !vehicle.currentRoad().equals(road)) {
                    crossed.add(id);
                }
            });
        }

        assertEquals(waiting.keySet(), crossed, "vehicles still waiting where they queued");
        awaitNetworkQuiet();
        for (IntersectionMutex mutex : world.mutexes().values()) {
            assertEquals(null, mutex.holder(), mutex.intersectionId());
            assertEquals(List.of(), mutex.waiting(), mutex.intersectionId());
        }
    }

    @Test
    void ricartAgrawalaWritesLamportAndRicartEvidence() throws Exception {
        build(MutexMode.RICART_AGRAWALA);
        for (int tick = 1; tick <= 600; tick++) {
            step(tick);
        }
        engine.stop();
        network.stop();
        SimLogger.shutdown();

        List<String> lines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
        assertTrue(lines.stream().anyMatch(line -> line.matches("\\[LAMPORT \\d+] G\\d+/\\S+ -> .+")),
                "no [LAMPORT] lines");
        assertTrue(lines.stream().anyMatch(line -> line.matches("\\[RICART] G\\d+/\\S+ requested access at \\d+")));
        assertTrue(lines.stream().anyMatch(line -> line.matches("\\[RICART] G\\d+/\\S+ ENTERED critical section")));
        assertTrue(lines.stream().anyMatch(line -> line.matches("\\[RICART] G\\d+/\\S+ released critical section")));
    }

    private void build(MutexMode mode) {
        var props = new Properties();
        props.setProperty(SimConfig.ENGINE_PARALLEL, "true");
        props.setProperty(SimConfig.VEHICLE_MAX_COUNT, Integer.toString(MAX_VEHICLES));
        props.setProperty(SimConfig.SPAWN_INTERVAL_MILLIS, "100");
        props.setProperty(SimConfig.DISTRIBUTED_MUTEX, mode.name());
        props.setProperty(SimConfig.DISTRIBUTED_LATENCY_MIN_MILLIS, "0");
        props.setProperty(SimConfig.DISTRIBUTED_LATENCY_MAX_MILLIS, "2");
        SimConfig cfg = SimConfig.from(props);
        RoadGraph graph = grid();
        network = new SimulatedNetwork(cfg);
        network.start();
        lights = new LightController(graph, cfg);
        world = new WorldState(graph, cfg, lights, new IntersectionZones(graph, cfg), network,
                IntersectionMutex.forGraph(graph, network));
        engine = new SimulationEngine(cfg, world, new VehicleSpawner(world, cfg));
    }

    private void step(int tick) {
        engine.step();
        if (tick % TICKS_PER_LIGHT_PHASE == 0) {
            for (TrafficLight light : lights.lights()) {
                TestLights.force(light, light.state().next());
            }
        }
    }

    // Los que cruzan con la sección de Ricart-Agrawala vienen todos del mismo acceso y lo tienen concedido.
    private void checkOneAccessPerBox(int tick) {
        Map<Intersection, Set<Road>> accesses = new HashMap<>();
        world.raCrossings().forEach((id, road) -> {
            accesses.computeIfAbsent(road.to(), at -> new HashSet<>()).add(road);
            IntersectionMutex mutex = world.mutexes().get(road.to());
            assertTrue(mutex.isGranted(id), "tick " + tick + ": " + id + " crosses " + road.to().id()
                    + " without the section");
        });
        accesses.forEach((at, roads) -> assertTrue(roads.size() <= 1,
                "tick " + tick + ": box " + at.id() + " has vehicles from " + roads));
    }

    private Map<String, Vehicle> vehiclesById() {
        Map<String, Vehicle> byId = new HashMap<>();
        world.vehicles().forEach(vehicle -> byId.put(vehicle.id(), vehicle));
        return byId;
    }

    private void awaitNetworkQuiet() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (network.inFlightCount() > 0 && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
    }

    private static RoadGraph grid() {
        var graph = new RoadGraph();
        var nodes = new Intersection[ROWS][COLUMNS];
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLUMNS; c++) {
                nodes[r][c] = new Intersection("G" + r + c, c * SPACING, r * SPACING);
                graph.addVertex(nodes[r][c]);
            }
        }
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLUMNS; c++) {
                if (c + 1 < COLUMNS) {
                    graph.addTwoWayRoad("H" + r + c, nodes[r][c], nodes[r][c + 1], SPEED_LIMIT, 1);
                }
                if (r + 1 < ROWS) {
                    graph.addTwoWayRoad("V" + r + c, nodes[r][c], nodes[r + 1][c], SPEED_LIMIT, 1);
                }
            }
        }
        return graph;
    }

    private record Logs(String logFile) implements LogSettings {

        @Override
        public boolean logConsole() {
            return false;
        }

        @Override
        public SimLogger.Level logLevel() {
            return SimLogger.Level.INFO;
        }
    }
}
