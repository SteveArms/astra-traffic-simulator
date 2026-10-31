package com.astra.model;

import com.astra.graph.Intersection;
import com.astra.graph.Road;
import java.util.List;
import java.util.Objects;

/**
 * Vehículo que recorre una ruta de vías consecutivas.
 *
 * @implNote Solo astra-engine modifica su estado; la vista usa {@link #toView()}.
 */
public abstract class Vehicle {

    private final String id;
    private final VehicleKind kind;
    private final double maxSpeed;
    private final double acceleration;

    private List<Road> route;
    private int roadIndex;
    private double offset;
    private double speed;
    private boolean finished;

    protected Vehicle(String id, VehicleKind kind, List<Road> route, double maxSpeed, double acceleration) {
        this.id = Objects.requireNonNull(id, "id");
        this.kind = Objects.requireNonNull(kind, "kind");
        if (!(maxSpeed > 0) || !(acceleration > 0)) {
            throw new IllegalArgumentException("maxSpeed and acceleration must be > 0 for " + id);
        }
        this.maxSpeed = maxSpeed;
        this.acceleration = acceleration;
        assignRoute(route);
    }

    public final void assignRoute(List<Road> newRoute) {
        var copy = List.copyOf(newRoute);
        if (copy.isEmpty()) {
            throw new IllegalArgumentException("Route of " + id + " must not be empty");
        }
        for (int i = 1; i < copy.size(); i++) {
            if (!copy.get(i - 1).to().equals(copy.get(i).from())) {
                throw new IllegalArgumentException("Route of " + id + " is not connected at "
                        + copy.get(i - 1).id() + " -> " + copy.get(i).id());
            }
        }
        route = copy;
        roadIndex = 0;
        offset = 0;
        finished = false;
    }

    public void update(double dtSeconds) {
        if (finished) {
            return;
        }
        speed = Math.min(speed + acceleration * dtSeconds, speedCap());
        offset += speed * dtSeconds;
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
        return new VehicleView(id, kind, x(), y(), headingRad(), speed);
    }

    public Road currentRoad() {
        return route.get(roadIndex);
    }

    public Intersection destination() {
        return route.get(route.size() - 1).to();
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

    public List<Road> route() {
        return route;
    }

    public double offset() {
        return offset;
    }

    public double speed() {
        return speed;
    }

    public double maxSpeed() {
        return maxSpeed;
    }

    public double acceleration() {
        return acceleration;
    }

    private double speedCap() {
        return Math.min(maxSpeed, currentRoad().speedLimit());
    }

    private double progress() {
        return offset / currentRoad().length();
    }
}
