package com.astra.distributed;

import com.astra.graph.Graph;
import com.astra.graph.Intersection;
import com.astra.graph.Road;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Exclusión mutua de la zona central de una intersección con un grupo de Ricart-Agrawala: cada vía entrante es un
 * proceso ({@code "<intersectionId>/<roadId>"}) y sus pares son los demás accesos. Cada acceso tiene una cola FIFO de
 * vehículos y solo el primero compite por la sección crítica, con un pedido a la vez por nodo.
 *
 * <p>La API no bloquea: el motor encola, consulta {@link #isGranted} en cada tick y llama a {@link #release} al
 * salir. Si el primero de la cola cancela mientras su acceso espera los REPLY, el pedido sigue en curso y al entrar
 * pasa al nuevo primero, o se libera enseguida si la cola quedó vacía.
 *
 * <p>Orden de bloqueo: el lock de esta clase y después el de un nodo. astra-net entra aquí (al conceder la
 * sección) sin tener tomado el lock del nodo, así que no hay ciclo.
 *
 * @implNote {@link #enqueue}, {@link #release} y {@link #cancel} los llama astra-engine (o un worker); el callback
 *           de entrada corre en astra-net. {@link #isGranted}, {@link #holder} y {@link #waiting} son seguros desde
 *           cualquier hilo.
 */
public final class IntersectionMutex {

    private final String intersectionId;
    private final Map<String, Access> accessesByRoad;
    private final Map<String, Access> vehicles = new ConcurrentHashMap<>();
    private final ReentrantLock lock = new ReentrantLock();

    public IntersectionMutex(Intersection intersection, Collection<Road> incoming, SimulatedNetwork network) {
        Objects.requireNonNull(intersection, "intersection");
        Objects.requireNonNull(network, "network");
        this.intersectionId = intersection.id();
        List<Road> roads = new ArrayList<>(incoming);
        roads.sort(Comparator.comparing(Road::id));
        List<String> ids = new ArrayList<>();
        for (Road road : roads) {
            if (!road.to().equals(intersection)) {
                throw new IllegalArgumentException("Road " + road.id() + " does not enter " + intersectionId);
            }
            ids.add(processIdOf(road));
        }
        var accesses = new LinkedHashMap<String, Access>();
        for (Road road : roads) {
            String id = processIdOf(road);
            List<String> peers = ids.stream().filter(peer -> !peer.equals(id)).toList();
            var access = new Access(id, RicartAgrawalaNode.join(id, peers, network));
            accesses.put(road.id(), access);
        }
        this.accessesByRoad = Map.copyOf(accesses);
        accesses.values().forEach(access -> access.node.setOnEnter(() -> onEnter(access)));
    }

    /** Un mutex con todas las vías del grafo que entran a {@code intersection}. */
    public static IntersectionMutex of(Graph<Intersection, Road> graph, Intersection intersection,
            SimulatedNetwork network) {
        List<Road> incoming = graph.edges().stream().filter(road -> road.to().equals(intersection)).toList();
        return new IntersectionMutex(intersection, incoming, network);
    }

    public String intersectionId() {
        return intersectionId;
    }

    /**
     * Pone al vehículo al final de la cola de su acceso; si es el primero y el acceso está libre, pide la sección.
     *
     * @throws IllegalArgumentException si la vía no entra a esta intersección
     * @throws IllegalStateException si el vehículo ya está en alguna cola
     */
    public void enqueue(Road incoming, String vehicleId) {
        Objects.requireNonNull(incoming, "incoming");
        Objects.requireNonNull(vehicleId, "vehicleId");
        Access access = accessesByRoad.get(incoming.id());
        if (access == null || !incoming.to().id().equals(intersectionId)) {
            throw new IllegalArgumentException("Road " + incoming.id() + " does not enter " + intersectionId);
        }
        lock.lock();
        try {
            if (vehicles.putIfAbsent(vehicleId, access) != null) {
                throw new IllegalStateException(vehicleId + " is already queued at " + intersectionId);
            }
            access.queue.add(vehicleId);
            requestIfIdle(access);
        } finally {
            lock.unlock();
        }
    }

    /** {@code true} si el vehículo es el primero de su cola y su acceso tiene la sección crítica. */
    public boolean isGranted(String vehicleId) {
        Access access = vehicles.get(vehicleId);
        return access != null && vehicleId.equals(access.queue.peek()) && access.node.isHeld();
    }

    /**
     * El vehículo salió de la zona: libera la sección y, si quedan vehículos en su cola, pide la siguiente entrada.
     *
     * @throws IllegalStateException si el vehículo no tiene la sección
     */
    public void release(String vehicleId) {
        lock.lock();
        try {
            if (!isGranted(vehicleId)) {
                throw new IllegalStateException(vehicleId + " does not hold " + intersectionId);
            }
            Access access = vehicles.remove(vehicleId);
            access.queue.poll();
            access.node.releaseCS();
            requestIfIdle(access);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Saca al vehículo de su cola; si tenía la sección, la libera.
     *
     * @return {@code false} si el vehículo no estaba en ninguna cola
     */
    public boolean cancel(String vehicleId) {
        lock.lock();
        try {
            Access access = vehicles.remove(vehicleId);
            if (access == null) {
                return false;
            }
            boolean holding = vehicleId.equals(access.queue.peek()) && access.node.isHeld();
            access.queue.remove(vehicleId);
            if (holding) {
                access.node.releaseCS();
                requestIfIdle(access);
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    /** Vehículo que tiene la sección crítica, o {@code null} si nadie la tiene. */
    public String holder() {
        for (Access access : accessesByRoad.values()) {
            String head = access.queue.peek();
            if (head != null && access.node.isHeld()) {
                return head;
            }
        }
        return null;
    }

    /** Vehículos en cola sin la sección, por acceso (en orden de id de vía) y en orden FIFO dentro de cada uno. */
    public List<String> waiting() {
        lock.lock();
        try {
            String holder = holder();
            List<String> result = new ArrayList<>();
            accessesByRoad.values().stream()
                    .sorted(Comparator.comparing(access -> access.processId))
                    .forEach(access -> access.queue.stream().filter(id -> !id.equals(holder)).forEach(result::add));
            return List.copyOf(result);
        } finally {
            lock.unlock();
        }
    }

    static String processIdOf(Road road) {
        return road.to().id() + "/" + road.id();
    }

    // Lock tomado.
    private void requestIfIdle(Access access) {
        if (!access.queue.isEmpty() && access.node.state() == RicartAgrawalaNode.State.RELEASED) {
            access.node.requestCS(access.processId + "#" + (++access.requests));
        }
    }

    // astra-net (o quien pidió, si el acceso no tiene pares): la cola pudo vaciarse mientras se esperaban los REPLY.
    private void onEnter(Access access) {
        lock.lock();
        try {
            if (access.queue.isEmpty()) {
                access.node.releaseCS();
            }
        } finally {
            lock.unlock();
        }
    }

    private static final class Access {

        final String processId;
        final RicartAgrawalaNode node;
        final ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();
        // Protegido por el lock del mutex.
        long requests;

        Access(String processId, RicartAgrawalaNode node) {
            this.processId = processId;
            this.node = node;
        }
    }
}
