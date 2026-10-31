package com.astra.core;

import com.astra.graph.Intersection;
import com.astra.graph.Road;
import com.astra.graph.RoadGraph;
import com.astra.graph.algo.Dijkstra;
import com.astra.model.Car;
import com.astra.model.Vehicle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.ToDoubleFunction;

/** Autos con rutas aleatorias que se renuevan al llegar. Temporal: el Día 4 lo reemplaza VehicleSpawner. */
public final class DemoScenario {

    private DemoScenario() {
    }

    /** @implNote Called before the engine starts; the registered hook runs on astra-engine. */
    public static void seed(WorldState world, SimConfig cfg, long seed) {
        RoadGraph graph = world.graph();
        var random = new Random(seed);
        var dijkstra = new Dijkstra<Intersection, Road>();
        // Orden fijo para que la semilla sea reproducible.
        List<Intersection> intersections = new ArrayList<>(graph.vertices());
        intersections.sort(Comparator.comparing(Intersection::id));

        List<Intersection> origins = new ArrayList<>(intersections);
        Collections.shuffle(origins, random);
        for (int i = 0; i < cfg.demoCarCount(); i++) {
            Intersection origin = origins.get(i % origins.size());
            List<Road> route = randomRoute(graph, dijkstra, intersections, random, origin, null)
                    .orElseThrow(() -> new IllegalStateException("No route from " + origin.id()));
            world.addVehicle(new Car(route, cfg.carMaxSpeed(), cfg.carAcceleration(), cfg.carDeceleration()));
        }

        world.setOnVehicleFinished(vehicle -> reroute(vehicle, graph, dijkstra, intersections, random));
    }

    private static void reroute(Vehicle vehicle, RoadGraph graph, Dijkstra<Intersection, Road> dijkstra,
            List<Intersection> intersections, Random random) {
        randomRoute(graph, dijkstra, intersections, random, vehicle.destination(), vehicle.currentRoad())
                .ifPresent(vehicle::assignRoute);
    }

    // Sin giro en U, salvo que sea la única salida (callejón sin salida).
    static Optional<List<Road>> randomRoute(RoadGraph graph, Dijkstra<Intersection, Road> dijkstra,
            List<Intersection> intersections, Random random, Intersection origin, Road arrivedVia) {
        ToDoubleFunction<Road> noUTurn = road -> isUTurn(road, arrivedVia) ? Double.POSITIVE_INFINITY : road.weight();
        Optional<List<Road>> route = firstReachable(graph, dijkstra, intersections, random, origin, noUTurn);
        return route.isPresent() ? route : firstReachable(graph, dijkstra, intersections, random, origin, Road::weight);
    }

    private static boolean isUTurn(Road road, Road arrivedVia) {
        return arrivedVia != null && road.from().equals(arrivedVia.to()) && road.to().equals(arrivedVia.from());
    }

    private static Optional<List<Road>> firstReachable(RoadGraph graph, Dijkstra<Intersection, Road> dijkstra,
            List<Intersection> intersections, Random random, Intersection origin, ToDoubleFunction<Road> weight) {
        List<Intersection> candidates = new ArrayList<>(intersections);
        candidates.remove(origin);
        while (!candidates.isEmpty()) {
            Intersection destination = candidates.remove(random.nextInt(candidates.size()));
            List<Road> route = dijkstra.shortestPath(graph, origin, destination, weight);
            if (!route.isEmpty()) {
                return Optional.of(route);
            }
        }
        return Optional.empty();
    }
}
