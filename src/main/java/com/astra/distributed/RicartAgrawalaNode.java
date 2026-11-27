package com.astra.distributed;

import com.astra.util.SimLogger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Un proceso de Ricart-Agrawala (1981). Se comunica con sus pares solo por mensajes de {@link SimulatedNetwork}:
 * para entrar a la sección crítica envía un REQUEST a cada par y entra al recibir los N-1 REPLY. Un par responde
 * enseguida salvo que esté dentro (HELD) o quiera entrar (WANTED) con una petición anterior según {@link Request};
 * en ese caso difiere la respuesta hasta salir. Cada entrada cuesta exactamente 2(N-1) mensajes.
 *
 * <p>El estado se protege con un {@link ReentrantLock}; los mensajes se preparan dentro y se envían fuera del lock.
 * Las líneas {@code [RICART]} se registran dentro, para que salgan en el orden en que cambió el estado.
 *
 * @implNote {@link #requestCS} y {@link #releaseCS} los llama el dueño del nodo (astra-engine o un test);
 *           {@link #onMessage} corre en astra-net. El callback de entrada corre en el hilo que completó la entrada
 *           (astra-net al llegar el último REPLY, o quien llamó a {@link #requestCS} si no hay pares), sin el lock.
 */
public final class RicartAgrawalaNode implements MessageHandler {

    public enum State { RELEASED, WANTED, HELD }

    private final String processId;
    private final Set<String> peers;
    private final SimulatedNetwork network;
    private final LamportClock clock;
    private final ReentrantLock lock = new ReentrantLock();
    private volatile Runnable onEnter = () -> { };

    // Protegidos por lock.
    private State state = State.RELEASED;
    private Request myRequest;
    private String myRequestId;
    private final Set<String> pendingReplies = new HashSet<>();
    // Par diferido -> id de su petición, que viaja en el REPLY. Orden de llegada.
    private final Map<String, String> deferred = new LinkedHashMap<>();

    private RicartAgrawalaNode(String processId, Collection<String> peers, SimulatedNetwork network) {
        this.processId = Objects.requireNonNull(processId, "processId");
        this.network = Objects.requireNonNull(network, "network");
        this.peers = Set.copyOf(peers);
        if (this.peers.contains(processId)) {
            throw new IllegalArgumentException(processId + " cannot be its own peer");
        }
        this.clock = new LamportClock(processId);
    }

    /** Crea el nodo y lo registra en la red con su {@code processId}. */
    public static RicartAgrawalaNode join(String processId, Collection<String> peers, SimulatedNetwork network) {
        var node = new RicartAgrawalaNode(processId, peers, network);
        network.register(processId, node);
        return node;
    }

    /**
     * Acción que se ejecuta cada vez que el nodo entra a la sección crítica, fuera del lock.
     *
     * @implNote Called once by the owner right after {@link #join}, before any request.
     */
    public void setOnEnter(Runnable onEnter) {
        this.onEnter = Objects.requireNonNull(onEnter, "onEnter");
    }

    public String processId() {
        return processId;
    }

    public Set<String> peers() {
        return peers;
    }

    LamportClock clock() {
        return clock;
    }

    /**
     * Pide la sección crítica: pasa a WANTED y envía un REQUEST a cada par. Sin pares entra directamente.
     *
     * @throws IllegalStateException si el nodo no está en RELEASED (un pedido a la vez)
     */
    public void requestCS(String requestId) {
        Objects.requireNonNull(requestId, "requestId");
        List<Message> outbox = new ArrayList<>();
        boolean entered;
        lock.lock();
        try {
            if (state != State.RELEASED) {
                throw new IllegalStateException(processId + " already " + state);
            }
            state = State.WANTED;
            int ts = clock.send("REQUEST CS");
            myRequest = new Request(ts, processId);
            myRequestId = requestId;
            SimLogger.ricart(processId + " requested access at " + ts);
            pendingReplies.clear();
            pendingReplies.addAll(peers);
            for (String peer : peers) {
                outbox.add(new Message(MessageType.REQUEST, processId, peer, ts, requestId));
            }
            entered = peers.isEmpty() && enterLocked();
        } finally {
            lock.unlock();
        }
        sendAll(outbox);
        if (entered) {
            onEnter.run();
        }
    }

    /**
     * Sale de la sección crítica y responde a los pedidos diferidos.
     *
     * @throws IllegalStateException si el nodo no está en HELD
     */
    public void releaseCS() {
        List<Message> outbox = new ArrayList<>();
        lock.lock();
        try {
            if (state != State.HELD) {
                throw new IllegalStateException(processId + " cannot release from " + state);
            }
            state = State.RELEASED;
            myRequest = null;
            myRequestId = null;
            SimLogger.ricart(processId + " released critical section");
            deferred.forEach((peer, requestId) -> outbox.add(reply(peer, requestId)));
            deferred.clear();
        } finally {
            lock.unlock();
        }
        sendAll(outbox);
    }

    /** @implNote Called from the astra-net thread only. */
    @Override
    public void onMessage(Message m) {
        switch (m.type()) {
            case REQUEST -> onRequest(m);
            case REPLY -> onReply(m);
        }
    }

    public boolean isHeld() {
        return state() == State.HELD;
    }

    public State state() {
        lock.lock();
        try {
            return state;
        } finally {
            lock.unlock();
        }
    }

    private void onRequest(Message m) {
        Message answer = null;
        lock.lock();
        try {
            clock.receive(m.timestamp(), "RECV REQUEST from " + m.from());
            var incoming = new Request(m.timestamp(), m.from());
            boolean defer = state == State.HELD || (state == State.WANTED && myRequest.compareTo(incoming) < 0);
            if (defer) {
                deferred.put(m.from(), m.requestId());
            } else {
                answer = reply(m.from(), m.requestId());
            }
        } finally {
            lock.unlock();
        }
        if (answer != null) {
            network.send(answer);
        }
    }

    private void onReply(Message m) {
        boolean entered = false;
        lock.lock();
        try {
            clock.receive(m.timestamp(), "RECV REPLY from " + m.from());
            if (state != State.WANTED || !m.requestId().equals(myRequestId) || !pendingReplies.remove(m.from())) {
                SimLogger.warn(SimLogger.Category.NET, processId + " ignored stale REPLY from " + m.from()
                        + " (" + m.requestId() + ")");
                return;
            }
            entered = pendingReplies.isEmpty() && enterLocked();
        } finally {
            lock.unlock();
        }
        if (entered) {
            onEnter.run();
        }
    }

    private boolean enterLocked() {
        state = State.HELD;
        SimLogger.ricart(processId + " ENTERED critical section");
        return true;
    }

    private Message reply(String peer, String requestId) {
        int ts = clock.send("REPLY to " + peer);
        return new Message(MessageType.REPLY, processId, peer, ts, requestId);
    }

    private void sendAll(List<Message> outbox) {
        for (Message m : outbox) {
            network.send(m);
        }
    }
}
