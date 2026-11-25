package com.astra.parking;

import com.astra.graph.Graph;
import com.astra.graph.Intersection;
import com.astra.graph.Road;
import com.astra.graph.algo.Dijkstra;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Registro de las playas de un mapa y de sus salidas. Las estrategias lo consultan para elegir otra playa o el
 * gateway más cercano; "cercano" es el costo de Dijkstra (largo de las vías) desde un nodo.
 *
 * @implNote Las playas son seguras desde cualquier hilo; las búsquedas las hace astra-engine en la fase C.
 */
public final class ParkingDirectory {

    private final Graph<Intersection, Road> graph;
    private final Map<Intersection, ParkingLot> lots = new LinkedHashMap<>();
    private final List<Intersection> gateways;
    private final Dijkstra<Intersection, Road> dijkstra = new Dijkstra<>();

    public ParkingDirectory(Graph<Intersection, Road> graph) {
        this.graph = Objects.requireNonNull(graph, "graph");
        graph.vertices().stream()
                .filter(Intersection::isParking)
                .sorted(Comparator.comparing(Intersection::id))
                .forEach(node -> lots.put(node, new ParkingLot(node)));
        this.gateways = graph.vertices().stream()
                .filter(Intersection::isGateway)
                .sorted(Comparator.comparing(Intersection::id))
                .toList();
    }

    /** Playas ordenadas por id. */
    public List<ParkingLot> lots() {
        return List.copyOf(lots.values());
    }

    public Optional<ParkingLot> lotAt(Intersection node) {
        return Optional.ofNullable(lots.get(node));
    }

    public List<Intersection> gateways() {
        return gateways;
    }

    /** Cajones libres de cada playa por id; una playa cerrada cuenta 0. */
    public Map<String, Integer> availability() {
        Map<String, Integer> free = new LinkedHashMap<>();
        lots.values().forEach(lot -> free.put(lot.id(), lot.isOpen() ? lot.spots().available() : 0));
        return free;
    }

    /** La playa abierta y con lugar más cercana a {@code from}, sin contar {@code excluded}. */
    public Optional<ParkingLot> nearestWithSpace(Intersection from, ParkingLot excluded) {
        return lots.values().stream()
                .filter(lot -> lot != excluded && lot.hasSpace())
                .filter(lot -> Double.isFinite(cost(from, lot.node())))
                .min(Comparator.comparingDouble((ParkingLot lot) -> cost(from, lot.node()))
                        .thenComparing(ParkingLot::id));
    }

    /** El gateway más cercano a {@code from}, o vacío si el mapa no tiene o no se llega a ninguno. */
    public Optional<Intersection> nearestGateway(Intersection from) {
        return gateways.stream()
                .filter(g -> Double.isFinite(cost(from, g)))
                .min(Comparator.comparingDouble((Intersection g) -> cost(from, g)).thenComparing(Intersection::id));
    }

    /** Largo del camino más corto, o infinito si no hay camino. */
    public double cost(Intersection from, Intersection to) {
        if (from.equals(to)) {
            return 0;
        }
        List<Road> path = dijkstra.shortestPath(graph, from, to);
        return path.isEmpty() ? Double.POSITIVE_INFINITY : Dijkstra.totalWeight(path, Road::weight);
    }
}
