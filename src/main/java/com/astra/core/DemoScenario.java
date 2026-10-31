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

/**
 * Escenario de demostración: autos con rutas aleatorias que, al llegar, reciben otra ruta.
 *
 * <p><b>Temporal:</b> el Día 4 lo reemplaza {@code VehicleSpawner}.
 */
public final class DemoScenario {

    private DemoScenario() {
    }

    /**
     * @throws IllegalStateException si el mapa no tiene dos intersecciones conectadas
     * @implNote Called from the setup thread before the engine starts; the hook it registers
     * runs on astra-engine, the only thread that uses the {@link Random} afterwards.
     */
    public static void seed(WorldState world, SimConfig cfg, long seed) {
        RoadGraph graph = world.graph();
        var random = new Random(seed);
        var dijkstra = new Dijkstra<Intersection, Road>();
        // Orden fijo: Set.copyOf no garantiza orden y la semilla debe dar siempre el mismo escenario.
        List<Intersection> intersections = new ArrayList<>(graph.vertices());
        intersections.sort(Comparator.comparing(Intersection::id));

        // Orígenes distintos mientras alcancen, para que los autos no arranquen superpuestos.
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

    // Prueba destinos en orden aleatorio hasta encontrar uno alcanzable.
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
