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
            List<Road> route = randomRoute(graph, dijkstra, intersections, random, origin)
                    .orElseThrow(() -> new IllegalStateException("No route from " + origin.id()));
            world.addVehicle(new Car(route, cfg.carMaxSpeed(), cfg.carAcceleration()));
        }

        world.setOnVehicleFinished(vehicle -> reroute(vehicle, graph, dijkstra, intersections, random));
    }

    private static void reroute(Vehicle vehicle, RoadGraph graph, Dijkstra<Intersection, Road> dijkstra,
            List<Intersection> intersections, Random random) {
        randomRoute(graph, dijkstra, intersections, random, vehicle.destination())
                .ifPresent(vehicle::assignRoute);
    }

    private static Optional<List<Road>> randomRoute(RoadGraph graph, Dijkstra<Intersection, Road> dijkstra,
            List<Intersection> intersections, Random random, Intersection origin) {
        List<Intersection> candidates = new ArrayList<>(intersections);
        candidates.remove(origin);
        while (!candidates.isEmpty()) {
            Intersection destination = candidates.remove(random.nextInt(candidates.size()));
            List<Road> route = dijkstra.shortestPath(graph, origin, destination);
            if (!route.isEmpty()) {
                return Optional.of(route);
            }
        }
        return Optional.empty();
    }
}
