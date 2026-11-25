package com.astra.core;

import com.astra.distributed.IntersectionMutex;
import com.astra.distributed.SimulatedNetwork;
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
import com.astra.traffic.QueueSensor;
import com.astra.traffic.TrafficLight;
import com.astra.util.SimLogger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
 *       lock), {@code entryClaims.putIfAbsent}, las entradas propias de {@code held}, {@code preempted},
 *       {@code raQueued}, {@code grantTicks} y {@code raCrossings} ({@link ConcurrentHashMap}) y, en modo
 *       {@link MutexMode#RICART_AGRAWALA}, {@link IntersectionMutex} (su lock; nunca bloquea en la red).</li>
 *   <li><b>B · MOVE</b> (mismos hilos y bloques que A): cada vehículo aplica {@code update(dt, plan[i])} y solo
 *       modifica su propio estado.</li>
 *   <li><b>C · COMMIT</b> (siempre astra-engine, secuencial): libera zonas y semáforos, recalcula rutas
 *       adaptativas, elimina a los que llegaron, ejecuta el spawner y reconstruye el índice por vía, la
 *       {@link RoadOccupancy} y las colas por intersección y eje.</li>
 * </ol>
 * El {@link java.util.concurrent.Phaser} de {@link ParallelUpdater} separa A de B y da la relación
 * <i>happens-before</i> entre las escrituras de los workers y la fase C.
 *
 * <p>Con {@link MutexMode#RICART_AGRAWALA}, un vehículo se encola en el {@link IntersectionMutex} de la intersección
 * una sola vez, cuando ya podría cruzar por las demás reglas (luz, espacio y cercanía a la línea), y solo cruza si
 * además tiene la sección crítica; la zona de capacidad sigue como segunda capa. Si tiene la sección y no logra
 * entrar en {@code distributed.grantTimeoutTicks}, la suelta y vuelve a encolarse; si su luz deja de permitirle
 * cruzar, sale de la cola. Al salir del box la libera. Los vehículos que nacen en el centro de una intersección no
 * cruzan desde ningún acceso, así que no pasan por Ricart-Agrawala.
 *
 * @implNote Su único dueño es astra-engine; los demás hilos usan {@link WorldSnapshot} o {@link #queueLength}.
 */
public final class WorldState implements QueueSensor {

    private static final int PHASE_TIMEOUT_TICKS = 2;

    private final RoadGraph graph;
    private final LightController lights;
    private final IntersectionZones zones;
    private final double minGap;
    private final double stopLineOffset;
    private final double boxLength;
    private final double approachMargin;
    private final double preemptDistance;
    private final double queueRadius;
    private final int grantTimeoutTicks;
    // null: sin capa distribuida (solo modo SEMAPHORE).
    private final SimulatedNetwork network;
    private final Map<Intersection, IntersectionMutex> mutexes;
    // Lo escribe astra-engine entre ticks; los workers lo leen en la fase A.
    private volatile MutexMode mutexMode;
    // Mutex en cuya cola está cada vehículo, o cuya sección tiene; en la fase A cada worker toca solo los suyos.
    private final Map<String, IntersectionMutex> raQueued = new ConcurrentHashMap<>();
    // Ticks seguidos con la sección sin haber entrado a la zona.
    private final Map<String, Integer> grantTicks = new ConcurrentHashMap<>();
    // Vía por la que entró a la zona cada vehículo que cruza con la sección de Ricart-Agrawala.
    private final Map<String, Road> raCrossings = new ConcurrentHashMap<>();
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
    private final AtomicReference<Map<Intersection, Map<Axis, Integer>>> queues = new AtomicReference<>(Map.of());
    private final SimStats stats;
    private long spawned;
    private long despawned;

    /** Sin capa distribuida: solo admite el modo {@link MutexMode#SEMAPHORE}. */
    public WorldState(RoadGraph graph, SimConfig cfg, LightController lights, IntersectionZones zones) {
        this(graph, cfg, lights, zones, null, Map.of());
    }

    /**
     * @param network red de los {@code mutexes}; solo se lee para el snapshot ({@code null} si no hay)
     * @param mutexes un {@link IntersectionMutex} por intersección con vías entrantes
     */
    public WorldState(RoadGraph graph, SimConfig cfg, LightController lights, IntersectionZones zones,
            SimulatedNetwork network, Map<Intersection, IntersectionMutex> mutexes) {
        this.graph = Objects.requireNonNull(graph, "graph");
        this.lights = Objects.requireNonNull(lights, "lights");
        this.zones = Objects.requireNonNull(zones, "zones");
        this.minGap = cfg.minGap();
        this.stopLineOffset = cfg.stopLineOffset();
        this.boxLength = cfg.boxLength();
        this.approachMargin = cfg.approachMargin();
        this.preemptDistance = cfg.emergencyPreemptDistance();
        this.queueRadius = cfg.smartQueueRadius();
        this.grantTimeoutTicks = cfg.grantTimeoutTicks();
        this.network = network;
        this.mutexes = Map.copyOf(Objects.requireNonNull(mutexes, "mutexes"));
        requireMutexesFor(cfg.mutexMode());
        this.mutexMode = cfg.mutexMode();
        this.stats = new SimStats(cfg);
        this.carFootprint = cfg.minGap() + cfg.specFor(VehicleKind.CAR).length();
        this.updater = cfg.engineParallel()
                ? new ParallelUpdater(cfg.engineWorkers(), cfg.tickMillis().multipliedBy(PHASE_TIMEOUT_TICKS))
                : null;
        lights.setQueueSensor(this);
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

    public LightController lights() {
        return lights;
    }

    public Optional<VehicleSpawner> spawner() {
        return Optional.ofNullable(spawner);
    }

    /** @implNote Safe to call from any thread. */
    public MutexMode mutexMode() {
        return mutexMode;
    }

    /**
     * Cambia el modo de exclusión mutua. Antes cancela todas las peticiones de Ricart-Agrawala: los vehículos que ya
     * están dentro de una zona terminan de cruzar con su permiso del semáforo de capacidad.
     *
     * @return {@code false} si ya estaba en ese modo
     * @throws IllegalStateException si se pide Ricart-Agrawala sin mutex
     * @implNote Called from astra-engine between ticks (SetMutexModeCmd), never during the phases.
     */
    public boolean setMutexMode(MutexMode mode) {
        Objects.requireNonNull(mode, "mode");
        if (mode == mutexMode) {
            return false;
        }
        requireMutexesFor(mode);
        raQueued.forEach((vehicleId, mutex) -> mutex.cancel(vehicleId));
        raQueued.clear();
        grantTicks.clear();
        raCrossings.clear();
        mutexMode = mode;
        SimLogger.info(SimLogger.Category.ENGINE, "mutex mode -> " + mode);
        return true;
    }

    /** Mutex por intersección; vacío sin capa distribuida. */
    public Map<Intersection, IntersectionMutex> mutexes() {
        return mutexes;
    }

    /**
     * Vía por la que entró cada vehículo que está cruzando una zona con la sección de Ricart-Agrawala.
     *
     * @implNote Called from astra-engine, or from tests with the engine stopped.
     */
    public Map<String, Road> raCrossings() {
        return Map.copyOf(raCrossings);
    }

    /** Vehículos encolados en algún mutex o con su sección. @implNote Called from astra-engine or tests. */
    public Set<String> raQueuedVehicles() {
        return Set.copyOf(raQueued.keySet());
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
                    state.name(), zone.occupancy(), zone.capacity(), queueLength(at, Axis.NS),
                    queueLength(at, Axis.EW)));
        }
        lightViews.sort(Comparator.comparing(LightView::intersectionId));
        List<MutexView> mutexViews = new ArrayList<>();
        mutexes.forEach((at, mutex) -> mutexViews.add(new MutexView(at.id(), mutex.holder(), mutex.waiting().size())));
        mutexViews.sort(Comparator.comparing(MutexView::intersectionId));
        NetworkView net = network == null ? NetworkView.EMPTY
                : new NetworkView(network.sentCount(), network.deliveredCount(), network.inFlightCount());
        return new WorldSnapshot(tick, simTimeSeconds, views, lightViews, spawned, despawned, activeByKind(), paused,
                speedFactor, stats.toView(), lights.mode(), mutexMode, mutexViews, net);
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
        stats.advance(dtSeconds);
        for (int i = 0; i < before.length; i++) {
            Vehicle vehicle = vehicles.get(i);
            releaseIfClear(vehicle);
            if (vehicle.isFinished()) {
                releaseAll(vehicle);
                despawned++;
                stats.recordArrival(vehicle.kind(), vehicle.tripSeconds(), vehicle.waitSeconds());
            } else if (!vehicle.currentRoad().equals(before[i]) && vehicle.strategy().isAdaptive()) {
                vehicle.reroute(graph, vehicle.currentRoad().to());
                // Una petición pendiente para la siguiente intersección se pidió con la ruta vieja.
                leaveQueue(vehicle.id(), vehicle.currentRoad().to());
            }
        }
        vehicles.removeIf(Vehicle::isFinished);
        if (spawner != null) {
            spawner.update(dtSeconds);
        }
        rebuildIndex();
        occupancy.set(measureOccupancy());
        Map<Intersection, Map<Axis, Integer>> measured = measureQueues();
        queues.set(measured);
        int totalQueue = measured.values().stream()
                .flatMap(byAxis -> byAxis.values().stream())
                .mapToInt(Integer::intValue)
                .sum();
        stats.sample(vehicles.size(), totalQueue);
    }

    /** @implNote Called from astra-lights-N: lee el último mapa inmutable publicado en la fase C. */
    @Override
    public int queueLength(Intersection at, Axis axis) {
        Map<Axis, Integer> byAxis = queues.get().get(at);
        return byAxis == null ? 0 : byAxis.getOrDefault(axis, 0);
    }

    // Demanda, no solo detenidos: en el eje en verde casi nadie está parado y SMART nunca extendería.
    private Map<Intersection, Map<Axis, Integer>> measureQueues() {
        Map<Intersection, Map<Axis, Integer>> counts = new HashMap<>();
        for (Vehicle vehicle : vehicles) {
            if (vehicle.remainingOnRoad() <= queueRadius) {
                Road road = vehicle.currentRoad();
                counts.computeIfAbsent(road.to(), i -> new EnumMap<>(Axis.class))
                        .merge(Axis.of(road), 1, Integer::sum);
            }
        }
        Map<Intersection, Map<Axis, Integer>> frozen = new HashMap<>();
        counts.forEach((at, byAxis) -> frozen.put(at, Map.copyOf(byAxis)));
        return Map.copyOf(frozen);
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
        boolean ricart = mutexMode == MutexMode.RICART_AGRAWALA;
        // Con la zona ya reservada está comprometido: cruza aunque la luz cambie.
        boolean committed = zone.contains(vehicle.id());
        if (!committed) {
            if (toLine >= stopLineOffset + approachMargin || !lightAllows(road, toLine)) {
                if (ricart) {
                    // Ya no podría cruzar: no ocupa la cola ni retiene la sección.
                    leaveQueue(vehicle.id(), road.to());
                }
                return toLine;
            }
        }
        double space = next.map(nextRoad -> spaceOn(nextRoad, byRoad)).orElse(0.0);
        if (ricart && !committed && !ricartAllows(vehicle, road, space >= 0)) {
            return toLine;
        }
        if (space < 0 || !zone.tryEnter(vehicle.id())) {
            return toLine;
        }
        if (ricart && !committed) {
            raCrossings.put(vehicle.id(), road);
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

    // Fase A: encola una sola vez y deja pasar solo con la sección. Toca solo las entradas de su propio vehículo.
    private boolean ricartAllows(Vehicle vehicle, Road road, boolean hasSpace) {
        IntersectionMutex mutex = mutexes.get(road.to());
        String id = vehicle.id();
        IntersectionMutex queuedAt = raQueued.get(id);
        if (queuedAt == null) {
            if (!hasSpace) {
                return false;
            }
            raQueued.put(id, mutex);
            mutex.enqueue(road, id);
        } else if (queuedAt != mutex) {
            // Todavía tiene la sección de la intersección anterior: la suelta al salir de su box.
            return false;
        }
        if (!mutex.isGranted(id)) {
            return false;
        }
        int ticks = grantTicks.merge(id, 1, Integer::sum);
        if (ticks > grantTimeoutTicks) {
            leaveQueue(id, road.to());
            SimLogger.info(SimLogger.Category.ENGINE, id + " gave up the section of " + road.to().id() + " after "
                    + grantTimeoutTicks + " ticks without crossing");
            return false;
        }
        return hasSpace;
    }

    // Sale de la cola (o suelta la sección) del mutex de {@code at}, si está en él.
    private void leaveQueue(String vehicleId, Intersection at) {
        IntersectionMutex mutex = raQueued.get(vehicleId);
        if (mutex == null || mutex != mutexes.get(at)) {
            return;
        }
        mutex.cancel(vehicleId);
        forgetRicart(vehicleId);
    }

    // Fase C: salió del box de {@code at}; libera la sección para el siguiente.
    private void finishCrossing(String vehicleId, Intersection at) {
        IntersectionMutex mutex = raQueued.get(vehicleId);
        if (mutex == null || mutex != mutexes.get(at)) {
            return;
        }
        if (mutex.isGranted(vehicleId)) {
            mutex.release(vehicleId);
        } else {
            mutex.cancel(vehicleId);
        }
        forgetRicart(vehicleId);
    }

    private void forgetRicart(String vehicleId) {
        raQueued.remove(vehicleId);
        grantTicks.remove(vehicleId);
        raCrossings.remove(vehicleId);
    }

    private void requireMutexesFor(MutexMode mode) {
        if (mode == MutexMode.RICART_AGRAWALA && mutexes.isEmpty()) {
            throw new IllegalStateException("Ricart-Agrawala mode needs an IntersectionMutex per intersection");
        }
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
            finishCrossing(vehicle.id(), zone.intersection());
            if (zone.intersection().equals(preempted.get(vehicle.id()))) {
                releasePreemption(vehicle.id());
            }
        }
    }

    private void releaseAll(Vehicle vehicle) {
        zones.releaseAll(vehicle.id());
        held.remove(vehicle.id());
        IntersectionMutex mutex = raQueued.get(vehicle.id());
        if (mutex != null) {
            mutex.cancel(vehicle.id());
            forgetRicart(vehicle.id());
        }
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
