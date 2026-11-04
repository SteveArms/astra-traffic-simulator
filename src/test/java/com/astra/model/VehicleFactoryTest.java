package com.astra.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.astra.graph.Intersection;
import com.astra.graph.Road;
import com.astra.graph.RoadGraph;
import com.astra.routing.RouteStrategy;
import com.astra.routing.ShortestDistanceStrategy;
import com.astra.util.MapLoader;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VehicleFactoryTest {

    private static final Map<VehicleKind, VehicleSpec> SPECS = new EnumMap<>(Map.of(
            VehicleKind.CAR, new VehicleSpec(14, 3, 6, 12),
            VehicleKind.BUS, new VehicleSpec(9, 1.5, 4, 20),
            VehicleKind.AMBULANCE, new VehicleSpec(20, 4, 8, 14)));

    private final RoadGraph city = MapLoader.load("maps/city-3x3.json").graph();
    private final RouteStrategy shortest = new ShortestDistanceStrategy();
    private final VehicleFactory factory = new VehicleFactory(SPECS::get);

    private Intersection at(String id) {
        return city.findIntersection(id).orElseThrow();
    }

    private Vehicle create(VehicleKind kind) {
        return factory.create(kind, shortest, at("I00"), at("I22"), city);
    }

    @Test
    void idPrefixAndClassMatchKind() {
        Vehicle car = create(VehicleKind.CAR);
        Vehicle bus = create(VehicleKind.BUS);
        Vehicle ambulance = create(VehicleKind.AMBULANCE);

        assertInstanceOf(Car.class, car);
        assertInstanceOf(Bus.class, bus);
        assertInstanceOf(Ambulance.class, ambulance);
        assertTrue(car.id().matches("CAR-\\d+"), car.id());
        assertTrue(bus.id().matches("BUS-\\d+"), bus.id());
        assertTrue(ambulance.id().matches("AMB-\\d+"), ambulance.id());
    }

    @Test
    void eachKindGetsItsSpec() {
        for (VehicleKind kind : VehicleKind.values()) {
            Vehicle vehicle = create(kind);
            assertEquals(kind, vehicle.kind());
            assertEquals(SPECS.get(kind), vehicle.spec());
            assertEquals(SPECS.get(kind).length(), vehicle.length());
        }
    }

    @Test
    void initialRouteGoesFromOriginToDestination() {
        Vehicle vehicle = create(VehicleKind.BUS);
        List<Road> route = vehicle.route();

        assertEquals(at("I00"), vehicle.origin());
        assertEquals(at("I22"), vehicle.destination());
        assertEquals(at("I00"), route.get(0).from());
        assertEquals(at("I22"), route.get(route.size() - 1).to());
        assertEquals(800, route.stream().mapToDouble(Road::length).sum(), 1e-9);
        assertEquals(shortest, vehicle.strategy());
    }

    @Test
    void onlyAmbulanceHasEmergencyPriority() {
        assertFalse(create(VehicleKind.CAR).hasEmergencyPriority());
        assertFalse(create(VehicleKind.BUS).hasEmergencyPriority());
        assertTrue(create(VehicleKind.AMBULANCE).hasEmergencyPriority());
    }

    @Test
    void emptyRouteThrows() {
        var island = new Intersection("ISLAND", 0, 0);
        city.addVertex(island);

        assertThrows(IllegalStateException.class,
                () -> factory.create(VehicleKind.CAR, shortest, at("I00"), island, city));
        assertThrows(IllegalStateException.class,
                () -> factory.create(VehicleKind.CAR, shortest, at("I00"), at("I00"), city));
    }
}
