package com.astra.core;

import com.astra.graph.Graph;
import com.astra.graph.Intersection;
import com.astra.graph.Road;
import com.astra.graph.RoadGraph;
import com.astra.model.Vehicle;
import com.astra.model.VehicleFactory;
import com.astra.model.VehicleKind;
import com.astra.routing.RouteStrategy;
import com.astra.routing.ShortestDistanceStrategy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Autos con destinos aleatorios; al llegar, otro auto sale desde ese destino. Temporal: el Día 4 lo reemplaza
 * VehicleSpawner.
 */
public final class DemoScenario {

    private DemoScenario() {
    }

    /** @implNote Called before the engine starts; the registered hook runs on astra-engine. */
    public static void seed(WorldState world, SimConfig cfg, long seed) {
        RoadGraph graph = world.graph();
        var random = new Random(seed);
        var factory = new VehicleFactory(cfg);
        RouteStrategy strategy = new ShortestDistanceStrategy();
        // Orden fijo para que la semilla sea reproducible.
        List<Intersection> intersections = new ArrayList<>(graph.vertices());
        intersections.sort(Comparator.comparing(Intersection::id));

        List<Intersection> origins = new ArrayList<>(intersections);
        Collections.shuffle(origins, random);
        for (int i = 0; i < cfg.demoCarCount(); i++) {
            Intersection origin = origins.get(i % origins.size());
            Intersection destination = randomDestination(graph, strategy, intersections, random, origin, null)
                    .orElseThrow(() -> new IllegalStateException("No route from " + origin.id()));
            world.addVehicle(factory.create(VehicleKind.CAR, strategy, origin, destination, graph));
        }

        world.setOnVehicleFinished(vehicle -> replace(world, factory, vehicle, intersections, random));
    }

    private static void replace(WorldState world, VehicleFactory factory, Vehicle arrived,
            List<Intersection> intersections, Random random) {
        Intersection origin = arrived.destination();
        randomDestination(world.graph(), arrived.strategy(), intersections, random, origin, arrived.currentRoad())
                .ifPresent(destination -> world.addVehicle(
                        factory.create(arrived.kind(), arrived.strategy(), origin, destination, world.graph())));
    }

    // Sin giro en U al salir, salvo que sea la única opción (callejón sin salida).
    static Optional<Intersection> randomDestination(Graph<Intersection, Road> graph, RouteStrategy strategy,
            List<Intersection> intersections, Random random, Intersection origin, Road arrivedVia) {
        List<Intersection> candidates = new ArrayList<>(intersections);
        candidates.remove(origin);
        Collections.shuffle(candidates, random);
        Intersection fallback = null;
        for (Intersection candidate : candidates) {
            List<Road> route = strategy.route(graph, origin, candidate);
            if (route.isEmpty()) {
                continue;
            }
            if (!isUTurn(route.get(0), arrivedVia)) {
                return Optional.of(candidate);
            }
            if (fallback == null) {
                fallback = candidate;
            }
        }
        return Optional.ofNullable(fallback);
    }

    private static boolean isUTurn(Road road, Road arrivedVia) {
        return arrivedVia != null && road.from().equals(arrivedVia.to()) && road.to().equals(arrivedVia.from());
    }
}
