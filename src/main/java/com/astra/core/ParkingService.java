package com.astra.core;

import com.astra.graph.Intersection;
import com.astra.graph.Road;
import com.astra.graph.RoadGraph;
import com.astra.model.Vehicle;
import com.astra.parking.ParkingDirectory;
import com.astra.parking.ParkingLot;
import com.astra.parking.ParkingSearchPolicy;
import com.astra.parking.SearchDecision;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.function.Predicate;

/**
 * Llegadas, esperas y salidas de las playas. Un vehículo que estaciona sale de la lista activa del mundo y vuelve,
 * con el tramo hasta su gateway de salida, cuando se cumple su estancia y la entrada tiene espacio. Las estancias se
 * miden en tiempo simulado, así que la pausa las congela.
 *
 * @implNote Solo astra-engine lo usa, en la fase C y en orden de la lista de vehículos: con la misma semilla es
 *           determinista.
 */
final class ParkingService {

    /** Qué pasó al llegar (o seguir esperando) en una playa. */
    enum Arrival { PARKED, WAITING, REDIRECTED }

    // Separa la semilla de las estancias de la del spawner.
    private static final long SEED_SALT = 0x9A2C;
    private static final double MILLIS_PER_SECOND = 1_000.0;

    private record Parked(Vehicle vehicle, ParkingLot lot, double departAt) {
    }

    /** Búsqueda en curso: desde cuándo, cuántas llegadas fallidas y hasta cuándo espera (NaN si no espera). */
    private static final class Search {
        final double startedAt;
        int failedAttempts;
        double waitUntil = Double.NaN;

        Search(double startedAt) {
            this.startedAt = startedAt;
        }
    }

    private final RoadGraph graph;
    private final ParkingDirectory directory;
    private final ParkingSearchPolicy policy;
    private final SimStats stats;
    private final Random random;
    private final double minStay;
    private final double maxStay;
    private final List<Parked> parked = new ArrayList<>();
    private final Map<String, Search> searches = new HashMap<>();

    ParkingService(RoadGraph graph, SimConfig cfg, SimStats stats) {
        this.graph = Objects.requireNonNull(graph, "graph");
        this.directory = new ParkingDirectory(graph);
        this.policy = ParkingSearchPolicy.from(cfg);
        this.stats = Objects.requireNonNull(stats, "stats");
        this.random = new Random(cfg.spawnSeed() ^ SEED_SALT);
        this.minStay = cfg.parkingMinStay().toMillis() / MILLIS_PER_SECOND;
        this.maxStay = cfg.parkingMaxStay().toMillis() / MILLIS_PER_SECOND;
    }

    ParkingDirectory directory() {
        return directory;
    }

    /** Vehículos estacionados ahora. */
    int parkedCount() {
        return parked.size();
    }

    /**
     * El vehículo terminó su tramo en una playa (o sigue esperando en su entrada). Si estaciona, el llamador lo saca
     * de la lista activa; si lo redirige, ya tiene su tramo nuevo.
     */
    Arrival arrive(Vehicle vehicle, double now) {
        ParkingLot lot = directory.lotAt(vehicle.destination())
                .orElseThrow(() -> new IllegalStateException(vehicle.destination().id() + " is not a parking lot"));
        Search search = searches.computeIfAbsent(vehicle.id(), id -> new Search(now));
        boolean waiting = !Double.isNaN(search.waitUntil);
        if (waiting) {
            // Mientras espera no cuenta rechazos: solo entra si se liberó un cajón.
            if (lot.hasSpace() && lot.tryPark(vehicle.id())) {
                return park(vehicle, lot, search, now);
            }
            if (now < search.waitUntil) {
                return Arrival.WAITING;
            }
            search.waitUntil = Double.NaN;
        } else {
            if (lot.tryPark(vehicle.id())) {
                return park(vehicle, lot, search, now);
            }
            search.failedAttempts++;
            stats.recordParkingRejection();
        }
        return apply(vehicle, lot, search, policy.onFull(vehicle, lot, directory, search.failedAttempts), now);
    }

    /**
     * Devuelve a la calle a los vehículos cuya estancia terminó, con el tramo hasta su gateway de salida, si
     * {@code canEnter} acepta la primera vía; si no, lo reintenta en el próximo tick. Libera su cajón.
     */
    List<Vehicle> departures(double now, Predicate<Road> canEnter) {
        List<Vehicle> leaving = new ArrayList<>();
        for (Iterator<Parked> it = parked.iterator(); it.hasNext(); ) {
            Parked p = it.next();
            if (p.departAt() > now) {
                continue;
            }
            Vehicle vehicle = p.vehicle();
            List<Road> route = vehicle.strategy().route(graph, p.lot().node(), vehicle.finalDestination());
            if (route.isEmpty() || !canEnter.test(route.get(0))) {
                continue;
            }
            vehicle.startLeg(route);
            p.lot().leave(vehicle.id());
            it.remove();
            leaving.add(vehicle);
        }
        return leaving;
    }

    /** Cada playa con sus autos y el cajón que ocupan. */
    List<ParkingView> views() {
        Map<String, Vehicle> byId = new HashMap<>();
        parked.forEach(p -> byId.put(p.vehicle().id(), p.vehicle()));
        List<ParkingView> views = new ArrayList<>();
        for (ParkingLot lot : directory.lots()) {
            List<ParkingView.ParkedCar> cars = new ArrayList<>();
            List<Optional<String>> slots = lot.spots().slots();
            for (int i = 0; i < slots.size(); i++) {
                int slot = i;
                slots.get(i).map(byId::get).ifPresent(
                        v -> cars.add(new ParkingView.ParkedCar(slot, v.id(), v.length())));
            }
            views.add(new ParkingView(lot.id(), lot.capacity(), lot.isOpen(), cars));
        }
        return views;
    }

    /** Ocupación total para las estadísticas. */
    void sampleOccupancy(double dtSeconds) {
        int occupied = 0;
        int capacity = 0;
        for (ParkingLot lot : directory.lots()) {
            occupied += lot.occupied();
            capacity += lot.capacity();
        }
        stats.sampleParkingOccupancy(occupied, capacity, dtSeconds);
    }

    /** El vehículo salió de la ciudad: se olvida su búsqueda. */
    void forget(String vehicleId) {
        searches.remove(vehicleId);
        policy.forget(vehicleId);
    }

    private Arrival park(Vehicle vehicle, ParkingLot lot, Search search, double now) {
        forget(vehicle.id());
        stats.recordParked(now - search.startedAt);
        parked.add(new Parked(vehicle, lot, now + minStay + random.nextDouble() * (maxStay - minStay)));
        return Arrival.PARKED;
    }

    private Arrival apply(Vehicle vehicle, ParkingLot lot, Search search, SearchDecision decision, double now) {
        return switch (decision) {
            case SearchDecision.Wait wait -> {
                search.waitUntil = now + wait.seconds();
                yield Arrival.WAITING;
            }
            case SearchDecision.Reroute reroute -> {
                if (!redirect(vehicle, lot.node(), reroute.target().node())) {
                    giveUp(vehicle, lot);
                }
                yield Arrival.REDIRECTED;
            }
            case SearchDecision.GiveUp giveUp -> {
                if (!redirect(vehicle, lot.node(), giveUp.gateway())) {
                    redirect(vehicle, lot.node(), vehicle.finalDestination());
                }
                forget(vehicle.id());
                yield Arrival.REDIRECTED;
            }
        };
    }

    private void giveUp(Vehicle vehicle, ParkingLot lot) {
        Intersection gateway = directory.nearestGateway(lot.node()).orElse(vehicle.finalDestination());
        redirect(vehicle, lot.node(), gateway);
        forget(vehicle.id());
    }

    private boolean redirect(Vehicle vehicle, Intersection from, Intersection to) {
        List<Road> leg = vehicle.strategy().route(graph, from, to);
        if (leg.isEmpty()) {
            return false;
        }
        vehicle.startLeg(leg);
        return true;
    }
}
