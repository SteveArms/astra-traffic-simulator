package com.astra.traffic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.astra.graph.Intersection;
import com.astra.graph.Road;
import com.astra.graph.RoadGraph;
import com.astra.util.MapLoader;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10)
class TrafficLightTest {

    private record FixedTimings(Duration greenSeconds, Duration yellowSeconds, Duration allRedSeconds)
            implements LightTimings {
    }

    private static final LightTimings FAST =
            new FixedTimings(Duration.ofMillis(100), Duration.ofMillis(50), Duration.ofMillis(50));

    private RoadGraph graph;
    private LightController controller;

    @BeforeEach
    void setUp() {
        graph = MapLoader.load("maps/city-3x3.json").graph();
        controller = new LightController(graph, FAST);
    }

    @AfterEach
    void tearDown() {
        controller.stop();
    }

    @Test
    void lightsAdvanceOnTheirOwn() throws InterruptedException {
        controller.start();
        Thread.sleep(1_000);

        assertEquals(5, controller.lights().size());
        for (TrafficLight light : controller.lights()) {
            assertTrue(light.transitions() > 3, light.intersection().id() + ": " + light.transitions());
        }
    }

    @Test
    void neverTwoAxesGreenAtTheSameTime() {
        controller.start();
        long end = System.nanoTime() + Duration.ofSeconds(1).toNanos();
        int samples = 0;
        while (System.nanoTime() < end) {
            for (TrafficLight light : controller.lights()) {
                // Una sola lectura para no mezclar estados.
                LightState state = light.state();
                assertFalse(state.colorFor(Axis.NS) == LightColor.GREEN
                        && state.colorFor(Axis.EW) == LightColor.GREEN, state.name());
            }
            samples++;
        }
        assertTrue(samples > 0);
    }

    @Test
    void stopKillsLightThreads() throws InterruptedException {
        controller.start();
        assertTrue(controller.isRunning());
        assertTrue(lightThreadsAlive());

        controller.stop();

        assertFalse(controller.isRunning());
        assertTrue(lightThreadsGone());
        controller.stop();
    }

    @Test
    void startTwiceThrows() {
        controller.start();

        assertThrows(IllegalStateException.class, controller::start);
    }

    @Test
    void adjacentIntersectionsStartWithOppositeAxes() {
        for (Road road : graph.edges()) {
            var from = controller.lightAt(road.from());
            var to = controller.lightAt(road.to());
            if (from.isPresent() && to.isPresent()) {
                assertNotEquals(from.get().state(), to.get().state(), road.id());
            }
        }
        assertEquals(new GreenState(Axis.EW), lightAt("I01").state());
        assertEquals(new GreenState(Axis.EW), lightAt("I10").state());
        assertEquals(new GreenState(Axis.NS), lightAt("I11").state());
    }

    @Test
    void canEnterFollowsLightOfDestination() {
        Road eastbound = graph.edges().stream()
                .filter(r -> r.from().id().equals("I00") && r.to().id().equals("I01")).findFirst().orElseThrow();
        Road southbound = graph.edges().stream()
                .filter(r -> r.from().id().equals("I01") && r.to().id().equals("I11")).findFirst().orElseThrow();

        assertTrue(controller.canEnter(eastbound));
        assertEquals(LightColor.GREEN, controller.colorFor(eastbound));
        assertTrue(controller.canEnter(southbound));

        Road eastIntoI11 = road("I10", "I11");
        assertFalse(controller.canEnter(eastIntoI11));
        assertEquals(LightColor.RED, controller.colorFor(eastIntoI11));

        Road intoCorner = road("I01", "I00");
        assertTrue(controller.canEnter(intoCorner));
        assertEquals(LightColor.GREEN, controller.colorFor(intoCorner));
    }

    @Test
    void cornersHaveNoLightButEdgesAndCenterDo() {
        for (String corner : List.of("I00", "I02", "I20", "I22")) {
            assertTrue(controller.lightAt(graph.findIntersection(corner).orElseThrow()).isEmpty(), corner);
        }
        for (String id : List.of("I01", "I10", "I11", "I12", "I21")) {
            assertTrue(controller.lightAt(graph.findIntersection(id).orElseThrow()).isPresent(), id);
        }
    }

    @Test
    void intersectionWithOneAxisHasNoLight() {
        var line = new RoadGraph();
        var a = new Intersection("A", 0, 0);
        var b = new Intersection("B", 100, 0);
        var c = new Intersection("C", 200, 0);
        List.of(a, b, c).forEach(line::addVertex);
        line.addTwoWayRoad("AB", a, b, 10, 1);
        Road bc = line.addTwoWayRoad("BC", b, c, 10, 1).get(0);

        var straight = new LightController(line, FAST);

        assertTrue(straight.lights().isEmpty());
        assertTrue(straight.lightAt(b).isEmpty());
        assertTrue(straight.canEnter(bc));
        assertEquals(LightColor.GREEN, straight.colorFor(bc));
    }

    private Road road(String from, String to) {
        return graph.edges().stream()
                .filter(r -> r.from().id().equals(from) && r.to().id().equals(to)).findFirst().orElseThrow();
    }

    private TrafficLight lightAt(String id) {
        return controller.lightAt(graph.findIntersection(id).orElseThrow()).orElseThrow();
    }

    private static boolean lightThreadsAlive() {
        return Thread.getAllStackTraces().keySet().stream()
                .anyMatch(t -> t.getName().startsWith(LightController.THREAD_PREFIX) && t.isAlive());
    }

    // Los hilos tardan unos ms en morir tras awaitTermination.
    private static boolean lightThreadsGone() throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            if (!lightThreadsAlive()) {
                return true;
            }
            Thread.sleep(10);
        }
        return false;
    }
}
