package com.astra.core;

import com.astra.graph.Intersection;
import com.astra.graph.Road;
import com.astra.graph.RoadGraph;
import com.astra.model.Vehicle;
import com.astra.model.VehicleView;
import com.astra.traffic.Axis;
import com.astra.traffic.LightColor;
import com.astra.traffic.LightController;
import com.astra.traffic.LightState;
import com.astra.traffic.TrafficLight;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Vehículos en circulación; cada tick limita su avance por el de adelante, el semáforo y la zona.
 *
 * @implNote Su único dueño es astra-engine; los demás hilos usan {@link WorldSnapshot}.
 */
public final class WorldState {

    private final RoadGraph graph;
    private final LightController lights;
    private final IntersectionZones zones;
    private final double minGap;
    private final double stopLineOffset;
    private final double boxLength;
    private final double approachMargin;
    private final double preemptDistance;
    private final List<Vehicle> vehicles = new ArrayList<>();
    private final Map<String, IntersectionZone> held = new HashMap<>();
    // Intersección cuyo semáforo pidió cada vehículo de emergencia.
    private final Map<String, Intersection> preempted = new HashMap<>();
    private Consumer<Vehicle> onVehicleFinished = vehicle -> { };

    public WorldState(RoadGraph graph, SimConfig cfg, LightController lights, IntersectionZones zones) {
        this.graph = Objects.requireNonNull(graph, "graph");
        this.lights = Objects.requireNonNull(lights, "lights");
        this.zones = Objects.requireNonNull(zones, "zones");
        this.minGap = cfg.minGap();
        this.stopLineOffset = cfg.stopLineOffset();
        this.boxLength = cfg.boxLength();
        this.approachMargin = cfg.approachMargin();
        this.preemptDistance = cfg.emergencyPreemptDistance();
    }

    public RoadGraph graph() {
        return graph;
    }

    public IntersectionZones zones() {
        return zones;
    }

    /**
     * Nace en el centro de la intersección de origen, así que intenta reservar su zona hasta salir del box.
     *
     * @implNote Called from astra-engine, or before the engine starts.
     */
    public void addVehicle(Vehicle vehicle) {
        vehicles.add(Objects.requireNonNull(vehicle, "vehicle"));
        IntersectionZone start = zones.zoneOf(vehicle.currentRoad().from());
        if (start.tryEnter(vehicle.id())) {
            held.put(vehicle.id(), start);
        }
    }

    /**
     * El vehículo que llega siempre se elimina; el gancho puede agregar otro con {@link #addVehicle}.
     *
     * @implNote The hook runs on astra-engine, after the finished vehicle released its zones.
     */
    public void setOnVehicleFinished(Consumer<Vehicle> hook) {
        onVehicleFinished = Objects.requireNonNull(hook, "hook");
    }

    /** @implNote Called from astra-engine. */
    public void update(double dtSeconds) {
        Map<Road, List<Vehicle>> byRoad = vehiclesByRoad();
        // Tamaño fijo: el gancho puede agregar vehículos durante el recorrido.
        int count = vehicles.size();
        for (int i = 0; i < count; i++) {
            Vehicle vehicle = vehicles.get(i);
            Road before = vehicle.currentRoad();
            requestPreemption(vehicle);
            vehicle.update(dtSeconds, freeDistance(vehicle, byRoad));
            releaseIfClear(vehicle);
            if (vehicle.isFinished()) {
                releaseAll(vehicle);
                onVehicleFinished.accept(vehicle);
            } else if (!vehicle.currentRoad().equals(before) && vehicle.strategy().isAdaptive()) {
                vehicle.reroute(graph, vehicle.currentRoad().to());
            }
            relocate(byRoad, vehicle, before);
        }
        vehicles.removeIf(Vehicle::isFinished);
    }

    /** @implNote Called from astra-engine. */
    public WorldSnapshot toSnapshot(long tick, double simTimeSeconds) {
        List<VehicleView> views = new ArrayList<>(vehicles.size());
        for (Vehicle vehicle : vehicles) {
            views.add(vehicle.toView());
        }
        List<LightView> lightViews = new ArrayList<>();
        for (TrafficLight light : lights.lights()) {
            // Una sola lectura del estado: NS y EW salen del mismo estado.
            LightState state = light.state();
            Intersection at = light.intersection();
            IntersectionZone zone = zones.zoneOf(at);
            lightViews.add(new LightView(at.id(), at.x(), at.y(), state.colorFor(Axis.NS), state.colorFor(Axis.EW),
                    state.name(), zone.occupancy(), zone.capacity()));
        }
        lightViews.sort(Comparator.comparing(LightView::intersectionId));
        return new WorldSnapshot(tick, simTimeSeconds, views, lightViews);
    }

    /** @implNote Called from astra-engine. */
    public int vehicleCount() {
        return vehicles.size();
    }

    /** @implNote Called from astra-engine, or from tests with the engine stopped. */
    public List<Vehicle> vehicles() {
        return Collections.unmodifiableList(vehicles);
    }

    private double freeDistance(Vehicle vehicle, Map<Road, List<Vehicle>> byRoad) {
        double gapLeader = gapToLeader(vehicle, byRoad.get(vehicle.currentRoad()));
        return Math.min(gapLeader, intersectionLimit(vehicle, vehicle.nextRoad(), byRoad));
    }

    private double gapToLeader(Vehicle vehicle, List<Vehicle> sameRoad) {
        int index = sameRoad.indexOf(vehicle);
        if (index <= 0) {
            return Double.POSITIVE_INFINITY;
        }
        return sameRoad.get(index - 1).rearOffset() - vehicle.offset() - minGap;
    }

    // También en la última vía: el destino es el centro de la intersección, así que respeta luz y zona.
    private double intersectionLimit(Vehicle vehicle, Optional<Road> next, Map<Road, List<Vehicle>> byRoad) {
        Road road = vehicle.currentRoad();
        double toLine = vehicle.remainingOnRoad() - stopLineOffset;
        IntersectionZone zone = zones.zoneOf(road.to());
        // Con la zona ya reservada está comprometido: cruza aunque la luz cambie.
        if (!zone.contains(vehicle.id())) {
            if (toLine >= stopLineOffset + approachMargin || !lightAllows(road, toLine)) {
                return toLine;
            }
        }
        double space = next.map(nextRoad -> spaceOn(nextRoad, byRoad)).orElse(0.0);
        if (space < 0 || !zone.tryEnter(vehicle.id())) {
            return toLine;
        }
        IntersectionZone previous = held.put(vehicle.id(), zone);
        if (previous != null && previous != zone) {
            previous.leave(vehicle.id());
        }
        return vehicle.remainingOnRoad() + space;
    }

    private boolean lightAllows(Road road, double toLine) {
        LightColor color = lights.colorFor(road);
        return color == LightColor.GREEN || (color == LightColor.YELLOW && toLine < stopLineOffset);
    }

    private double spaceOn(Road road, Map<Road, List<Vehicle>> byRoad) {
        List<Vehicle> onRoad = byRoad.getOrDefault(road, List.of());
        if (onRoad.isEmpty()) {
            return road.length();
        }
        return onRoad.get(onRoad.size() - 1).rearOffset() - minGap;
    }

    // La preferencia solo adelanta el verde: la zona se sigue reservando con tryEnter.
    private void requestPreemption(Vehicle vehicle) {
        if (!vehicle.hasEmergencyPriority() || vehicle.remainingOnRoad() > preemptDistance) {
            return;
        }
        Road road = vehicle.currentRoad();
        Intersection target = road.to();
        if (target.equals(preempted.get(vehicle.id())) || lights.lightAt(target).isEmpty()) {
            return;
        }
        releasePreemption(vehicle.id());
        lights.preempt(road, vehicle.id());
        preempted.put(vehicle.id(), target);
    }

    private void releaseIfClear(Vehicle vehicle) {
        IntersectionZone zone = held.get(vehicle.id());
        if (zone != null && vehicle.currentRoad().from().equals(zone.intersection())
                && vehicle.offset() > boxLength) {
            zone.leave(vehicle.id());
            held.remove(vehicle.id());
            if (zone.intersection().equals(preempted.get(vehicle.id()))) {
                releasePreemption(vehicle.id());
            }
        }
    }

    private void releaseAll(Vehicle vehicle) {
        zones.releaseAll(vehicle.id());
        held.remove(vehicle.id());
        releasePreemption(vehicle.id());
    }

    private void releasePreemption(String vehicleId) {
        Intersection at = preempted.remove(vehicleId);
        if (at != null) {
            lights.release(at, vehicleId);
        }
    }

    private Map<Road, List<Vehicle>> vehiclesByRoad() {
        Map<Road, List<Vehicle>> byRoad = new HashMap<>();
        for (Road road : graph.edges()) {
            byRoad.put(road, new ArrayList<>());
        }
        for (Vehicle vehicle : vehicles) {
            byRoad.computeIfAbsent(vehicle.currentRoad(), r -> new ArrayList<>()).add(vehicle);
        }
        Comparator<Vehicle> farthestFirst = Comparator.comparingDouble(Vehicle::offset).reversed();
        byRoad.values().forEach(list -> list.sort(farthestFirst));
        return byRoad;
    }

    // Los que cambian de vía entran por detrás: su offset es el menor de la nueva vía.
    private void relocate(Map<Road, List<Vehicle>> byRoad, Vehicle vehicle, Road before) {
        if (vehicle.isFinished()) {
            byRoad.get(before).remove(vehicle);
        } else if (!vehicle.currentRoad().equals(before)) {
            byRoad.get(before).remove(vehicle);
            byRoad.computeIfAbsent(vehicle.currentRoad(), r -> new ArrayList<>()).add(vehicle);
        }
    }
}
