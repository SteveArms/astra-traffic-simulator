package com.astra.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.astra.graph.RoadGraph;
import com.astra.model.TripPurpose;
import com.astra.model.Vehicle;
import com.astra.model.VehicleKind;
import com.astra.parking.ParkingDirectory;
import com.astra.parking.ParkingLot;
import com.astra.traffic.LightController;
import com.astra.traffic.TestLights;
import com.astra.traffic.TrafficLight;
import com.astra.util.MapLoader;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.function.IntConsumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Viajes con estacionamiento de punta a punta en el mapa v2, con el motor en paralelo. */
@Timeout(120)
class ParkingIntegrationTest {

    private static final int TICKS = 5_000;
    private static final int TICKS_PER_LIGHT_PHASE = 100;

    @Test
    void vehiclesParkLeaveAndLotsNeverOverflow() {
        Run run = new Run(config("NEAREST_ALTERNATIVE"));
        ParkingDirectory directory = run.world.parkingDirectory().orElseThrow();
        Set<String> parkedOnce = new HashSet<>();
        Set<String> backOnStreet = new HashSet<>();

        run.run(tick -> {
            int parkedNow = 0;
            for (ParkingLot lot : directory.lots()) {
                assertTrue(lot.occupied() <= lot.capacity(), "tick " + tick + ": " + lot.id() + " overflows");
                assertEquals(lot.entries() - lot.exits(), lot.occupied(), "tick " + tick + ": " + lot.id());
                parkedNow += lot.occupied();
                lot.spots().slots().forEach(slot -> slot.ifPresent(parkedOnce::add));
            }
            assertEquals(run.world.parkedCount(), parkedNow, "tick " + tick);
            checkSnapshot(run.world, directory, tick);
            Set<String> active = new HashSet<>();
            for (Vehicle vehicle : run.world.vehicles()) {
                active.add(vehicle.id());
                if (parkedOnce.contains(vehicle.id())) {
                    backOnStreet.add(vehicle.id());
                }
            }
            for (ParkingLot lot : directory.lots()) {
                lot.spots().slots().forEach(slot -> slot.ifPresent(
                        id -> assertFalse(active.contains(id), id + " is parked and driving at once")));
            }
        });

        long entries = directory.lots().stream().mapToLong(ParkingLot::entries).sum();
        long exits = directory.lots().stream().mapToLong(ParkingLot::exits).sum();
        assertTrue(entries > 0, "nobody parked");
        assertTrue(exits > 0, "nobody left a parking lot");
        assertEquals(entries - exits, run.world.parkedCount());
        assertFalse(backOnStreet.isEmpty(), "no parked vehicle came back to the street");
        assertTrue(run.world.despawned() > 0, "throughput is zero");
        StatsView stats = run.world.toSnapshot(TICKS, 0, false, 1).stats();
        assertTrue(stats.avgParkingOccupancy() > 0);
    }

    // El snapshot muestra en cada cajón al mismo auto que ocupa la playa.
    private static void checkSnapshot(WorldState world, ParkingDirectory directory, int tick) {
        List<ParkingView> views = world.toSnapshot(tick, 0, false, 1).parkings();
        assertEquals(directory.lots().size(), views.size());
        for (int i = 0; i < views.size(); i++) {
            ParkingLot lot = directory.lots().get(i);
            ParkingView view = views.get(i);
            assertEquals(lot.id(), view.lotId());
            assertEquals(lot.occupied(), view.cars().size(), "tick " + tick + ": " + lot.id());
            for (ParkingView.ParkedCar car : view.cars()) {
                assertEquals(Optional.of(car.vehicleId()), lot.spots().slots().get(car.slot()));
                assertTrue(car.length() > 0);
            }
        }
    }

    @Test
    void parkTripsAreOnlyCarsAndEndAtAGateway() {
        Run run = new Run(config("WAIT_AT_ENTRANCE"));

        run.run(tick -> {
            for (Vehicle vehicle : run.world.vehicles()) {
                assertTrue(vehicle.finalDestination().isGateway(), vehicle.id());
                if (vehicle.purpose() == TripPurpose.PARK) {
                    assertEquals(VehicleKind.CAR, vehicle.kind(), vehicle.id());
                }
            }
        });

        assertTrue(run.world.despawned() > 0);
    }

    @Test
    void fullLotsMakeVehiclesSearchAndCountRejections() {
        // Estancias largas y muchos autos que estacionan: las playas se llenan y hay que buscar.
        var props = baseProps("GIVE_UP");
        props.setProperty(SimConfig.PARKING_TRIP_WEIGHT, "1");
        props.setProperty(SimConfig.PARKING_MIN_STAY_SECONDS, "200");
        props.setProperty(SimConfig.PARKING_MAX_STAY_SECONDS, "300");
        Run run = new Run(SimConfig.from(props));
        ParkingDirectory directory = run.world.parkingDirectory().orElseThrow();

        run.run(tick -> directory.lots().forEach(lot -> assertTrue(lot.occupied() <= lot.capacity())));

        long rejections = directory.lots().stream().mapToLong(ParkingLot::rejections).sum();
        assertTrue(rejections > 0, "no lot ever filled up");
        StatsView stats = run.world.toSnapshot(TICKS, 0, false, 1).stats();
        assertEquals(rejections, stats.parkingRejections());
        assertTrue(run.world.despawned() > 0, "vehicles that gave up never left");
    }

    @Test
    void sequentialRunsWithTheSameSeedParkIdentically() {
        var props = baseProps("WAIT_AT_ENTRANCE");
        props.setProperty(SimConfig.ENGINE_PARALLEL, "false");

        assertEquals(lotCounters(props), lotCounters(props));
    }

    // Entradas, salidas y rechazos de cada playa tras la corrida completa.
    private static List<Long> lotCounters(Properties props) {
        Run run = new Run(SimConfig.from(props), false);
        run.run(tick -> { });
        List<Long> counters = new ArrayList<>();
        for (ParkingLot lot : run.world.parkingDirectory().orElseThrow().lots()) {
            counters.add(lot.entries());
            counters.add(lot.exits());
            counters.add(lot.rejections());
        }
        assertTrue(counters.stream().mapToLong(Long::longValue).sum() > 0);
        return counters;
    }

    private static SimConfig config(String strategy) {
        return SimConfig.from(baseProps(strategy));
    }

    private static Properties baseProps(String strategy) {
        var props = new Properties();
        props.setProperty(SimConfig.ENGINE_PARALLEL, "true");
        props.setProperty(SimConfig.SPAWN_INTERVAL_MILLIS, "500");
        props.setProperty(SimConfig.PARKING_TRIP_WEIGHT, "0.6");
        props.setProperty(SimConfig.PARKING_MIN_STAY_SECONDS, "5");
        props.setProperty(SimConfig.PARKING_MAX_STAY_SECONDS, "20");
        props.setProperty(SimConfig.PARKING_SEARCH_STRATEGY, strategy);
        return props;
    }

    private static final class Run {
        final WorldState world;
        final SimulationEngine engine;
        final LightController lights;

        Run(SimConfig cfg) {
            this(cfg, true);
        }

        Run(SimConfig cfg, boolean parallel) {
            RoadGraph graph = MapLoader.load("maps/city-3x3-v2.json").graph();
            lights = new LightController(graph, cfg);
            world = new WorldState(graph, cfg, lights, new IntersectionZones(graph, cfg));
            engine = new SimulationEngine(cfg, world, new VehicleSpawner(world, cfg));
            assertEquals(parallel, world.isParallel());
        }

        void run(IntConsumer afterTick) {
            try {
                for (int tick = 1; tick <= TICKS; tick++) {
                    engine.step();
                    if (tick % TICKS_PER_LIGHT_PHASE == 0) {
                        for (TrafficLight light : List.copyOf(lights.lights())) {
                            TestLights.force(light, light.state().next());
                        }
                    }
                    afterTick.accept(tick);
                }
            } finally {
                engine.stop();
            }
        }
    }
}
