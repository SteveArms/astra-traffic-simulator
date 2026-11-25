package com.astra.parking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.astra.graph.Intersection;
import com.astra.graph.RoadGraph;
import com.astra.model.TripPurpose;
import com.astra.model.Vehicle;
import com.astra.model.VehicleFactory;
import com.astra.model.VehicleKind;
import com.astra.model.VehicleSpec;
import com.astra.routing.ShortestDistanceStrategy;
import com.astra.util.MapLoader;
import java.time.Duration;
import java.util.Comparator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ParkingSearchStrategyTest {

    private static final VehicleSpec SPEC = new VehicleSpec(14, 3, 6, 12);

    private RoadGraph graph;
    private ParkingDirectory directory;
    private ParkingLot p00;
    private Vehicle car;

    @BeforeEach
    void setUp() {
        graph = MapLoader.load("maps/city-3x3-v2.json").graph();
        directory = new ParkingDirectory(graph);
        p00 = lot("P00");
        fill(p00);
        Intersection gateway = node("G-N0");
        var strategy = new ShortestDistanceStrategy();
        car = new VehicleFactory(kind -> SPEC).create(VehicleKind.CAR, strategy,
                strategy.route(graph, gateway, p00.node()), TripPurpose.PARK, node("G-S2"));
    }

    @Test
    void nearestReroutesToTheClosestLotWithSpace() {
        ParkingLot expected = directory.lots().stream()
                .filter(lot -> lot != p00)
                .min(Comparator.comparingDouble(lot -> directory.cost(p00.node(), lot.node())))
                .orElseThrow();

        SearchDecision decision = new NearestAlternativeStrategy().onFull(car, p00, directory);

        var reroute = assertInstanceOf(SearchDecision.Reroute.class, decision);
        assertEquals(expected, reroute.target());
        assertTrue(reroute.target().hasSpace());
    }

    @Test
    void nearestSkipsFullAndClosedLots() {
        ParkingLot first = ((SearchDecision.Reroute) new NearestAlternativeStrategy().onFull(car, p00, directory))
                .target();
        fill(first);
        ParkingLot second = ((SearchDecision.Reroute) new NearestAlternativeStrategy().onFull(car, p00, directory))
                .target();
        second.setOpen(false);

        var third = assertInstanceOf(SearchDecision.Reroute.class,
                new NearestAlternativeStrategy().onFull(car, p00, directory));
        assertTrue(third.target() != first && third.target() != second && third.target() != p00);
    }

    @Test
    void nearestGivesUpWhenEveryLotIsFull() {
        directory.lots().forEach(ParkingSearchStrategyTest::fill);

        var decision = assertInstanceOf(SearchDecision.GiveUp.class,
                new NearestAlternativeStrategy().onFull(car, p00, directory));
        assertTrue(decision.gateway().isGateway());
    }

    @Test
    void waitFirstWaitsThenReroutes() {
        var wait = new WaitAtEntranceStrategy(Duration.ofSeconds(8));

        var first = assertInstanceOf(SearchDecision.Wait.class, wait.onFull(car, p00, directory));
        assertEquals(8, first.seconds());
        assertInstanceOf(SearchDecision.Reroute.class, wait.onFull(car, p00, directory));
        // Una visita nueva a la misma playa vuelve a esperar.
        assertInstanceOf(SearchDecision.Wait.class, wait.onFull(car, p00, directory));
    }

    @Test
    void forgetResetsTheWait() {
        var wait = new WaitAtEntranceStrategy(Duration.ofSeconds(8));
        wait.onFull(car, p00, directory);

        wait.forget(car.id());

        assertInstanceOf(SearchDecision.Wait.class, wait.onFull(car, p00, directory));
    }

    @Test
    void giveUpGoesToTheNearestGateway() {
        Intersection expected = directory.gateways().stream()
                .min(Comparator.comparingDouble(g -> directory.cost(p00.node(), g)))
                .orElseThrow();

        var decision = assertInstanceOf(SearchDecision.GiveUp.class,
                new GiveUpStrategy().onFull(car, p00, directory));

        assertEquals(expected, decision.gateway());
    }

    @Test
    void policyGivesUpAfterMaxAttemptsWhateverTheStrategy() {
        var nearest = new ParkingSearchPolicy(new NearestAlternativeStrategy(), 2);
        var waiting = new ParkingSearchPolicy(new WaitAtEntranceStrategy(Duration.ofSeconds(8)), 2);

        assertInstanceOf(SearchDecision.Reroute.class, nearest.onFull(car, p00, directory, 1));
        assertInstanceOf(SearchDecision.GiveUp.class, nearest.onFull(car, p00, directory, 2));
        assertInstanceOf(SearchDecision.Wait.class, waiting.onFull(car, p00, directory, 1));
        assertInstanceOf(SearchDecision.GiveUp.class, waiting.onFull(car, p00, directory, 3));
    }

    @Test
    void policyIsBuiltFromSettingsName() {
        assertEquals("WAIT_AT_ENTRANCE", ParkingSearchPolicy.from(settings("WAIT_AT_ENTRANCE")).strategy().name());
        assertEquals("GIVE_UP", ParkingSearchPolicy.from(settings("GIVE_UP")).strategy().name());
        assertThrows(IllegalArgumentException.class, () -> ParkingSearchPolicy.from(settings("TELEPORT")));
        assertThrows(IllegalArgumentException.class, () -> new ParkingSearchPolicy(new GiveUpStrategy(), 0));
    }

    private ParkingLot lot(String id) {
        return directory.lotAt(node(id)).orElseThrow();
    }

    private Intersection node(String id) {
        return graph.findIntersection(id).orElseThrow();
    }

    private static void fill(ParkingLot lot) {
        for (int i = 0; lot.hasSpace(); i++) {
            lot.tryPark(lot.id() + "-filler-" + i);
        }
    }

    private static ParkingSettings settings(String strategy) {
        return new ParkingSettings() {
            @Override
            public double parkingTripWeight() {
                return 0.35;
            }

            @Override
            public Duration parkingMinStay() {
                return Duration.ofSeconds(20);
            }

            @Override
            public Duration parkingMaxStay() {
                return Duration.ofSeconds(60);
            }

            @Override
            public String parkingSearchStrategy() {
                return strategy;
            }

            @Override
            public int parkingMaxSearchAttempts() {
                return 2;
            }

            @Override
            public Duration parkingWaitAtEntrance() {
                return Duration.ofSeconds(8);
            }
        };
    }
}
