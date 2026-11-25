package com.astra.parking;

import com.astra.graph.Intersection;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Playa de estacionamiento en un nodo {@link com.astra.graph.NodeKind#PARKING}: un cajón por plaza de su capacidad,
 * ocupado por el id de un vehículo. Se puede cerrar: cerrada rechaza a todos, pero los que ya estaban salen igual.
 *
 * @implNote Seguro desde cualquier hilo; en la simulación la usa astra-engine en la fase C.
 */
public final class ParkingLot {

    private final String id;
    private final Intersection node;
    private final ResourcePool<String> spots;
    private volatile boolean open = true;
    private final AtomicLong entries = new AtomicLong();
    private final AtomicLong rejections = new AtomicLong();
    private final AtomicLong exits = new AtomicLong();

    public ParkingLot(Intersection node) {
        this.node = Objects.requireNonNull(node, "node");
        if (!node.isParking()) {
            throw new IllegalArgumentException(node.id() + " is not a PARKING node");
        }
        this.id = node.id();
        this.spots = new ResourcePool<>(node.capacity());
    }

    /**
     * Intenta estacionar sin bloquear.
     *
     * @return {@code false} (y cuenta un rechazo) si está cerrada o llena
     */
    public boolean tryPark(String vehicleId) {
        Objects.requireNonNull(vehicleId, "vehicleId");
        if (open && spots.tryAcquire(vehicleId).isPresent()) {
            entries.incrementAndGet();
            return true;
        }
        rejections.incrementAndGet();
        return false;
    }

    /** Libera el cajón del vehículo; si no estaba estacionado aquí, no hace nada. */
    public void leave(String vehicleId) {
        Objects.requireNonNull(vehicleId, "vehicleId");
        if (holds(vehicleId)) {
            spots.release(vehicleId);
            exits.incrementAndGet();
        }
    }

    public boolean holds(String vehicleId) {
        return spots.slots().contains(Optional.of(vehicleId));
    }

    /** Abierta y con al menos un cajón libre. */
    public boolean hasSpace() {
        return open && spots.available() > 0;
    }

    public String id() {
        return id;
    }

    public Intersection node() {
        return node;
    }

    public ResourcePool<String> spots() {
        return spots;
    }

    public int capacity() {
        return spots.capacity();
    }

    public int occupied() {
        return spots.capacity() - spots.available();
    }

    public boolean isOpen() {
        return open;
    }

    public void setOpen(boolean open) {
        this.open = open;
    }

    public long entries() {
        return entries.get();
    }

    public long rejections() {
        return rejections.get();
    }

    public long exits() {
        return exits.get();
    }
}
