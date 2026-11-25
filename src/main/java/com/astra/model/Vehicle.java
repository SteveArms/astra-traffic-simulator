package com.astra.model;

import com.astra.graph.Graph;
import com.astra.graph.Intersection;
import com.astra.graph.Road;
import com.astra.routing.RouteStrategy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Vehículo que recorre una ruta de vías consecutivas de {@code origin} a {@code destination}.
 * {@code offset} es la posición del parachoques delantero sobre la vía actual.
 *
 * <p>Un viaje puede tener varios tramos: {@code destination} es el final del tramo en curso y
 * {@link #finalDestination()} el gateway por el que sale de la ciudad. Un viaje {@link TripPurpose#PARK} va primero a
 * una playa y, al salir de ella, {@link #startLeg} le da el tramo hasta su destino final.
 *
 * @implNote Solo astra-engine modifica su estado; la vista usa {@link #toView()}.
 */
public abstract class Vehicle {

    /** Por debajo de esta velocidad (px/s) el tiempo cuenta como espera. */
    public static final double WAITING_SPEED = 0.5;

    private final String id;
    private final VehicleKind kind;
    private final VehicleSpec spec;
    private final RouteStrategy strategy;
    private final Intersection origin;
    private final TripPurpose purpose;
    private final Intersection finalDestination;
    private Intersection destination;

    private List<Road> route = List.of();
    private int roadIndex;
    private double offset;
    private double speed;
    private boolean finished;
    private double tripSeconds;
    private double waitSeconds;

    /** La ruta se asigna después con {@link #assignRoute}; normalmente lo hace {@link VehicleFactory}. */
    protected Vehicle(String id, VehicleKind kind, VehicleSpec spec, RouteStrategy strategy, Intersection origin,
            Intersection destination) {
        this(id, kind, spec, strategy, origin, destination, TripPurpose.THROUGH, destination);
    }

    /** @param finalDestination dónde termina el viaje; en un THROUGH es el mismo {@code destination} */
    protected Vehicle(String id, VehicleKind kind, VehicleSpec spec, RouteStrategy strategy, Intersection origin,
            Intersection destination, TripPurpose purpose, Intersection finalDestination) {
        this.id = Objects.requireNonNull(id, "id");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.spec = Objects.requireNonNull(spec, "spec");
        this.strategy = Objects.requireNonNull(strategy, "strategy");
        this.origin = Objects.requireNonNull(origin, "origin");
        this.destination = Objects.requireNonNull(destination, "destination");
        this.purpose = Objects.requireNonNull(purpose, "purpose");
        this.finalDestination = Objects.requireNonNull(finalDestination, "finalDestination");
        if (purpose == TripPurpose.THROUGH && !finalDestination.equals(destination)) {
            throw new IllegalArgumentException("A THROUGH trip of " + id + " ends at its destination");
        }
    }

    /** Reemplaza la ruta y vuelve al inicio; debe ir de {@code origin} a {@code destination}. */
    public final void assignRoute(List<Road> newRoute) {
        var copy = List.copyOf(newRoute);
        requireConnected(copy);
        if (!copy.get(0).from().equals(origin)) {
            throw new IllegalArgumentException("Route of " + id + " must start at " + origin.id());
        }
        route = copy;
        roadIndex = 0;
        offset = 0;
        finished = false;
    }

    /**
     * Empieza un tramo nuevo desde donde terminó el anterior (el final de la vía actual), con su destino en el final
     * de {@code leg}. Lo usa una playa para mandar al vehículo a otra playa, a un gateway o, al salir, a su destino
     * final.
     *
     * @throws IllegalArgumentException si el tramo está vacío, no es conexo o no empieza donde está el vehículo
     * @implNote Called from astra-engine (fase C).
     */
    public void startLeg(List<Road> leg) {
        var copy = List.copyOf(leg);
        if (copy.isEmpty()) {
            throw new IllegalArgumentException("Leg of " + id + " must not be empty");
        }
        Intersection here = currentRoad().to();
        if (!copy.get(0).from().equals(here)) {
            throw new IllegalArgumentException("Leg of " + id + " must start at " + here.id());
        }
        Intersection previous = destination;
        destination = copy.get(copy.size() - 1).to();
        try {
            requireConnected(copy);
        } catch (IllegalArgumentException e) {
            destination = previous;
            throw e;
        }
        route = copy;
        roadIndex = 0;
        offset = 0;
        speed = 0;
        finished = false;
    }

    /**
     * Recalcula el resto de la ruta desde {@code from}, el final de la vía actual, que se conserva. Si la
     * estrategia no encuentra camino, la ruta no cambia.
     *
     * @implNote Called from astra-engine.
     */
    public void reroute(Graph<Intersection, Road> g, Intersection from) {
        Road current = currentRoad();
        if (!current.to().equals(from)) {
            throw new IllegalArgumentException("Reroute of " + id + " must start at the end of " + current.id());
        }
        List<Road> tail = strategy.route(g, from, destination);
        if (tail.isEmpty() && !from.equals(destination)) {
            return;
        }
        var newRoute = new ArrayList<Road>(tail.size() + 1);
        newRoute.add(current);
        newRoute.addAll(tail);
        var copy = List.copyOf(newRoute);
        requireConnected(copy);
        route = copy;
        roadIndex = 0;
    }

    /** Acumula también viaje y espera. @implNote Called from astra-worker-N (fase B), solo sobre sí mismo. */
    public void update(double dtSeconds, double freeDistance) {
        if (finished) {
            return;
        }
        move(dtSeconds, freeDistance);
        tripSeconds += dtSeconds;
        if (!finished && speed < WAITING_SPEED) {
            waitSeconds += dtSeconds;
        }
    }

    private void move(double dtSeconds, double freeDistance) {
        double free = Math.max(0, freeDistance);
        // Velocidad desde la que todavía se puede frenar dentro de free.
        double target = Math.min(speedCap(), Math.sqrt(2 * spec.deceleration() * free));
        speed = speed < target
                ? Math.min(target, speed + spec.acceleration() * dtSeconds)
                : Math.max(target, speed - spec.deceleration() * dtSeconds);
        double step = speed * dtSeconds;
        if (step > free) {
            step = free;
            speed = dtSeconds > 0 ? free / dtSeconds : 0;
        }
        offset += step;
        while (offset >= currentRoad().length()) {
            if (roadIndex == route.size() - 1) {
                offset = currentRoad().length();
                speed = 0;
                finished = true;
                return;
            }
            offset -= currentRoad().length();
            roadIndex++;
            speed = Math.min(speed, speedCap());
        }
    }

    public boolean hasEmergencyPriority() {
        return false;
    }

    public double x() {
        Road road = currentRoad();
        return road.from().x() + (road.to().x() - road.from().x()) * progress();
    }

    public double y() {
        Road road = currentRoad();
        return road.from().y() + (road.to().y() - road.from().y()) * progress();
    }

    public double headingRad() {
        Road road = currentRoad();
        return Math.atan2(road.to().y() - road.from().y(), road.to().x() - road.from().x());
    }

    public VehicleView toView() {
        return new VehicleView(id, kind, x(), y(), headingRad(), speed, spec.length(), strategy.name());
    }

    public Road currentRoad() {
        if (route.isEmpty()) {
            throw new IllegalStateException("Vehicle " + id + " has no route yet");
        }
        return route.get(roadIndex);
    }

    public Optional<Road> nextRoad() {
        return roadIndex + 1 < route.size() ? Optional.of(route.get(roadIndex + 1)) : Optional.empty();
    }

    public double remainingOnRoad() {
        return currentRoad().length() - offset;
    }

    /** Posición del parachoques trasero sobre la vía actual; negativa si aún asoma en la anterior. */
    public double rearOffset() {
        return offset - spec.length();
    }

    public boolean isFinished() {
        return finished;
    }

    public String id() {
        return id;
    }

    public VehicleKind kind() {
        return kind;
    }

    public VehicleSpec spec() {
        return spec;
    }

    public RouteStrategy strategy() {
        return strategy;
    }

    public Intersection origin() {
        return origin;
    }

    /** Final del tramo en curso. */
    public Intersection destination() {
        return destination;
    }

    public TripPurpose purpose() {
        return purpose;
    }

    /** Gateway por el que el vehículo sale de la ciudad al terminar su viaje. */
    public Intersection finalDestination() {
        return finalDestination;
    }

    public List<Road> route() {
        return route;
    }

    public double offset() {
        return offset;
    }

    public double speed() {
        return speed;
    }

    public double tripSeconds() {
        return tripSeconds;
    }

    public double waitSeconds() {
        return waitSeconds;
    }

    public double length() {
        return spec.length();
    }

    public double maxSpeed() {
        return spec.maxSpeed();
    }

    public double acceleration() {
        return spec.acceleration();
    }

    public double deceleration() {
        return spec.deceleration();
    }

    private void requireConnected(List<Road> candidate) {
        if (candidate.isEmpty()) {
            throw new IllegalArgumentException("Route of " + id + " must not be empty");
        }
        for (int i = 1; i < candidate.size(); i++) {
            if (!candidate.get(i - 1).to().equals(candidate.get(i).from())) {
                throw new IllegalArgumentException("Route of " + id + " is not connected at "
                        + candidate.get(i - 1).id() + " -> " + candidate.get(i).id());
            }
        }
        if (!candidate.get(candidate.size() - 1).to().equals(destination)) {
            throw new IllegalArgumentException("Route of " + id + " must end at " + destination.id());
        }
    }

    private double speedCap() {
        return Math.min(spec.maxSpeed(), currentRoad().speedLimit());
    }

    private double progress() {
        return offset / currentRoad().length();
    }
}
