package com.astra.core;

import com.astra.graph.Intersection;
import com.astra.graph.Road;
import com.astra.graph.RoadGraph;
import com.astra.model.Vehicle;
import com.astra.model.VehicleKind;
import com.astra.model.VehicleView;
import com.astra.routing.RoadOccupancy;
import com.astra.traffic.Axis;
import com.astra.traffic.LightColor;
import com.astra.traffic.LightController;
import com.astra.traffic.LightState;
import com.astra.traffic.TrafficLight;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntConsumer;

/**
 * Vehículos en circulación; cada tick limita su avance por el de adelante, el semáforo y la zona.
 *
 * <p>{@link #update} corre en tres fases:
 * <ol>
 *   <li><b>A · PLAN</b> (astra-worker-N en paralelo, o astra-engine con {@code engine.parallel=false}): cada
 *       vehículo calcula su distancia libre en {@code plan[i]}. Lee los offsets de los demás desde el índice por
 *       vía, que no cambia hasta la fase C, y ningún vehículo se mueve hasta la fase B: por eso no hay carreras
 *       de datos aunque los workers lean vehículos ajenos. Lo único que escribe es estado concurrente:
 *       {@code zone.tryEnter} (un {@link java.util.concurrent.Semaphore}), {@code preempt} del semáforo (su
 *       lock), {@code entryClaims.putIfAbsent} y las entradas propias de {@code held} y {@code preempted}
 *       ({@link ConcurrentHashMap}).</li>
 *   <li><b>B · MOVE</b> (mismos hilos y bloques que A): cada vehículo aplica {@code update(dt, plan[i])} y solo
 *       modifica su propio estado.</li>
 *   <li><b>C · COMMIT</b> (siempre astra-engine, secuencial): libera zonas y semáforos, recalcula rutas
 *       adaptativas, elimina a los que llegaron, ejecuta el spawner y reconstruye el índice por vía y la
 *       {@link RoadOccupancy}.</li>
 * </ol>
 * El {@link java.util.concurrent.Phaser} de {@link ParallelUpdater} separa A de B y da la relación
 * <i>happens-before</i> entre las escrituras de los workers y la fase C.
 *
 * @implNote Su único dueño es astra-engine; los demás hilos usan {@link WorldSnapshot}.
 */
public final class WorldState {

    private static final int PHASE_TIMEOUT_TICKS = 2;

    private final RoadGraph graph;
    private final LightController lights;
    private final IntersectionZones zones;
    private final double minGap;
    private final double stopLineOffset;
    private final double boxLength;
    private final double approachMargin;
    private final double preemptDistance;
    // Largo de vía que ocupa un auto con su hueco: base de la capacidad aproximada de cada vía.
    private final double carFootprint;
    private final List<Vehicle> vehicles = new ArrayList<>();
    // Concurrentes: en la fase A cada worker escribe la entrada de sus propios vehículos.
    private final Map<String, IntersectionZone> held = new ConcurrentHashMap<>();
    // Intersección cuyo semáforo pidió cada vehículo de emergencia.
    private final Map<String, Intersection> preempted = new ConcurrentHashMap<>();
    // Vía de entrada que reservó cada vía de salida en este tick; se limpia al empezar el tick.
    private final ConcurrentHashMap<Road, String> entryClaims = new ConcurrentHashMap<>();
    // null: fases A y B secuenciales.
    private final ParallelUpdater updater;
    private Map<Road, List<Vehicle>> byRoad = Map.of();
    private boolean indexDirty = true;
    private VehicleSpawner spawner;
    private final AtomicReference<RoadOccupancy> occupancy = new AtomicReference<>(RoadOccupancy.EMPTY);
    private long spawned;
    private long despawned;

    public WorldState(RoadGraph graph, SimConfig cfg, LightController lights, IntersectionZones zones) {
        this.graph = Objects.requireNonNull(graph, "graph");
        this.lights = Objects.requireNonNull(lights, "lights");
        this.zones = Objects.requireNonNull(zones, "zones");
        this.minGap = cfg.minGap();
        this.stopLineOffset = cfg.stopLineOffset();
        this.boxLength = cfg.boxLength();
        this.approachMargin = cfg.approachMargin();
        this.preemptDistance = cfg.emergencyPreemptDistance();
        this.carFootprint = cfg.minGap() + cfg.specFor(VehicleKind.CAR).length();
        this.updater = cfg.engineParallel()
                ? new ParallelUpdater(cfg.engineWorkers(), cfg.tickMillis().multipliedBy(PHASE_TIMEOUT_TICKS))
                : null;
    }

    /**
     * El spawner corre en la fase C de cada tick.
     *
     * @implNote Called before the engine starts.
     */
    public void setSpawner(VehicleSpawner spawner) {
        this.spawner = Objects.requireNonNull(spawner, "spawner");
    }

    public boolean isParallel() {
        return updater != null;
    }

    /** @implNote Called from SimulationEngine.stop(); un tick posterior vuelve a crear los workers. */
    public void shutdown() {
        if (updater != null) {
            updater.shutdown();
        }
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
        spawned++;
        indexDirty = true;
        IntersectionZone start = zones.zoneOf(vehicle.currentRoad().from());
        if (start.tryEnter(vehicle.id())) {
            held.put(vehicle.id(), start);
        }
    }

    /**
     * Si la primera vía tiene espacio para un vehículo nuevo de largo {@code length} en su inicio: el último
     * vehículo en ella tiene {@code rearOffset >= minGap + length}.
     *
     * @implNote Called from astra-engine.
     */
    public boolean hasRoomAtStart(Road road, double length) {
        for (Vehicle vehicle : vehicles) {
            if (vehicle.currentRoad().equals(road) && vehicle.rearOffset() < minGap + length) {
                return false;
            }
        }
        return true;
    }

    /**
     * Ocupación de cada vía al final del último tick, entre 0 y 1.
     *
     * @implNote Safe to call from any thread.
     */
    public RoadOccupancy currentOccupancy() {
        return occupancy.get();
    }

    /**
     * Un tick en fases A (PLAN), B (MOVE) y C (COMMIT); ver la descripción de la clase. El vehículo que termina su
     * ruta se elimina, libera sus zonas y cuenta como salido.
     *
     * @implNote Called from astra-engine.
     */
    public void update(double dtSeconds) {
        if (indexDirty) {
            rebuildIndex();
        }
        entryClaims.clear();
        List<Vehicle> current = vehicles;
        int count = current.size();
        double[] plan = new double[count];
        Road[] before = new Road[count];
        for (int i = 0; i < count; i++) {
            before[i] = current.get(i).currentRoad();
        }
        Map<Road, List<Vehicle>> index = byRoad;
        IntConsumer planTask = i -> plan[i] = planFor(current.get(i), index);
        IntConsumer moveTask = i -> current.get(i).update(dtSeconds, plan[i]);
        if (updater != null) {
            updater.run(current, planTask, moveTask);
        } else {
            for (int i = 0; i < count; i++) {
                planTask.accept(i);
            }
            for (int i = 0; i < count; i++) {
                moveTask.accept(i);
            }
        }
        commit(before, dtSeconds);
    }

    /** @implNote Called from astra-engine. */
    public WorldSnapshot toSnapshot(long tick, double simTimeSeconds, boolean paused, double speedFactor) {
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
        return new WorldSnapshot(tick, simTimeSeconds, views, lightViews, spawned, despawned, activeByKind(), paused,
                speedFactor);
    }

    /** @implNote Called from astra-engine. */
    public long spawned() {
        return spawned;
    }

    /** @implNote Called from astra-engine. */
    public long despawned() {
        return despawned;
    }

    /**
     * Todos los tipos, con 0 si no hay ninguno.
     *
     * @implNote Called from astra-engine.
     */
    public Map<VehicleKind, Integer> activeByKind() {
        Map<VehicleKind, Integer> counts = new EnumMap<>(VehicleKind.class);
        for (VehicleKind kind : VehicleKind.values()) {
            counts.put(kind, 0);
        }
        for (Vehicle vehicle : vehicles) {
            counts.merge(vehicle.kind(), 1, Integer::sum);
        }
        return counts;
    }

    /** @implNote Called from astra-engine. */
    public int vehicleCount() {
        return vehicles.size();
    }

    /** @implNote Called from astra-engine, or from tests with the engine stopped. */
    public List<Vehicle> vehicles() {
        return Collections.unmodifiableList(vehicles);
    }

    // Fase A. @implNote Called from astra-worker-N, or astra-engine when not parallel.
    private double planFor(Vehicle vehicle, Map<Road, List<Vehicle>> index) {
        requestPreemption(vehicle);
        return freeDistance(vehicle, index);
    }

    // Fase C. @implNote Called from astra-engine.
    private void commit(Road[] before, double dtSeconds) {
        for (int i = 0; i < before.length; i++) {
            Vehicle vehicle = vehicles.get(i);
            releaseIfClear(vehicle);
            if (vehicle.isFinished()) {
                releaseAll(vehicle);
                despawned++;
            } else if (!vehicle.currentRoad().equals(before[i]) && vehicle.strategy().isAdaptive()) {
                vehicle.reroute(graph, vehicle.currentRoad().to());
            }
        }
        vehicles.removeIf(Vehicle::isFinished);
        if (spawner != null) {
            spawner.update(dtSeconds);
        }
        rebuildIndex();
        occupancy.set(measureOccupancy());
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
        // Dos vías distintas no meten vehículos a la vez en la misma vía de salida: gana la primera que reserva.
        if (next.isPresent()) {
            String owner = entryClaims.putIfAbsent(next.get(), road.id());
            if (owner != null && !owner.equals(road.id())) {
                holdZone(vehicle, zone);
                return toLine;
            }
        }
        holdZone(vehicle, zone);
        return vehicle.remainingOnRoad() + space;
    }

    private void holdZone(Vehicle vehicle, IntersectionZone zone) {
        IntersectionZone previous = held.put(vehicle.id(), zone);
        if (previous != null && previous != zone) {
            previous.leave(vehicle.id());
        }
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

    // Vehículos en la vía / capacidad aproximada (length / (minGap + largo de auto) * lanes), tope 1.
    private RoadOccupancy measureOccupancy() {
        Map<Road, Integer> counts = new HashMap<>();
        for (Vehicle vehicle : vehicles) {
            counts.merge(vehicle.currentRoad(), 1, Integer::sum);
        }
        Map<Road, Double> ratios = new HashMap<>();
        counts.forEach((road, count) -> {
            double capacity = road.length() / carFootprint * road.lanes();
            ratios.put(road, Math.min(1.0, count / capacity));
        });
        return new RoadOccupancy(ratios);
    }

    // Fase C: los workers solo lo leen durante A, así que puede ser un HashMap normal.
    private void rebuildIndex() {
        Map<Road, List<Vehicle>> index = new HashMap<>();
        for (Road road : graph.edges()) {
            index.put(road, new ArrayList<>());
        }
        for (Vehicle vehicle : vehicles) {
            index.computeIfAbsent(vehicle.currentRoad(), r -> new ArrayList<>()).add(vehicle);
        }
        Comparator<Vehicle> farthestFirst = Comparator.comparingDouble(Vehicle::offset).reversed();
        index.values().forEach(list -> list.sort(farthestFirst));
        byRoad = index;
        indexDirty = false;
    }
}
