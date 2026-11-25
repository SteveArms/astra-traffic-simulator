package com.astra.distributed;

import com.astra.util.SimLogger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Red entre procesos: cada mensaje llega a su destino tras un retardo aleatorio entre {@link
 * NetworkSettings#latencyMin()} y {@link NetworkSettings#latencyMax()}. No garantiza orden FIFO: dos mensajes del
 * mismo emisor pueden llegar al revés, cosa que Ricart-Agrawala no necesita.
 *
 * <p>Todas las entregas, y por tanto todos los {@link MessageHandler}, corren en el único hilo daemon
 * {@value #THREAD_NAME}: un handler nunca corre en paralelo con otro, pero no debe bloquearse.
 *
 * <p>Pausa: se conecta a la compuerta con {@code PauseListener.of(net::onPause, net::onResume)}. Mientras está en
 * pausa, los mensajes que cumplen su retardo se retienen; al reanudar se entregan enseguida, porque su latencia ya
 * pasó.
 *
 * @implNote {@link #register}, {@link #send}, {@link #onPause} y {@link #onResume} son seguros desde cualquier
 *           hilo; {@link #start} y {@link #stop} los llama el hilo que arranca y detiene la app.
 */
public final class SimulatedNetwork {

    public static final String THREAD_NAME = "astra-net";

    private static final long STOP_TIMEOUT_MILLIS = 2_000;

    private final Map<String, MessageHandler> handlers = new ConcurrentHashMap<>();
    private final long latencyMinMillis;
    private final long latencyMaxMillis;
    private final ReentrantLock randomLock = new ReentrantLock();
    private final Random random;
    private final AtomicLong sent = new AtomicLong();
    private final AtomicLong delivered = new AtomicLong();
    private final AtomicLong inFlight = new AtomicLong();
    private final Object lifecycle = new Object();
    private final ReentrantLock pauseLock = new ReentrantLock();
    // Protegidos por pauseLock.
    private boolean paused;
    private final Deque<Message> held = new ArrayDeque<>();
    private volatile ScheduledExecutorService executor;

    public SimulatedNetwork(NetworkSettings settings) {
        Objects.requireNonNull(settings, "settings");
        this.latencyMinMillis = settings.latencyMin().toMillis();
        this.latencyMaxMillis = settings.latencyMax().toMillis();
        if (latencyMinMillis < 0 || latencyMaxMillis < latencyMinMillis) {
            throw new IllegalArgumentException(
                    "latency range must satisfy 0 <= min <= max: " + latencyMinMillis + ".." + latencyMaxMillis);
        }
        this.random = new Random(settings.networkSeed());
    }

    /** Asocia un proceso a su handler; si ya existía, lo reemplaza. */
    public void register(String processId, MessageHandler handler) {
        handlers.put(Objects.requireNonNull(processId, "processId"), Objects.requireNonNull(handler, "handler"));
    }

    public void unregister(String processId) {
        handlers.remove(processId);
    }

    /** Arranca el hilo {@value #THREAD_NAME}. Idempotente. */
    public void start() {
        synchronized (lifecycle) {
            if (executor != null) {
                return;
            }
            executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
                var thread = new Thread(runnable, THREAD_NAME);
                thread.setDaemon(true);
                return thread;
            });
        }
    }

    /** Detiene la red; los mensajes en vuelo se descartan. Idempotente. */
    public void stop() {
        ScheduledExecutorService current;
        synchronized (lifecycle) {
            current = executor;
            executor = null;
        }
        pauseLock.lock();
        try {
            held.clear();
        } finally {
            pauseLock.unlock();
        }
        if (current == null) {
            return;
        }
        current.shutdownNow();
        try {
            if (!current.awaitTermination(STOP_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                SimLogger.warn(SimLogger.Category.NET, THREAD_NAME + " did not stop in time");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Programa la entrega de {@code m} sin bloquear.
     *
     * @return {@code false} si el destino no existe o la red no está en marcha (se registra un WARN en NET)
     */
    public boolean send(Message m) {
        Objects.requireNonNull(m, "message");
        if (!handlers.containsKey(m.to())) {
            SimLogger.warn(SimLogger.Category.NET, "dropped " + describe(m) + ": unknown destination");
            return false;
        }
        ScheduledExecutorService current = executor;
        if (current == null) {
            SimLogger.warn(SimLogger.Category.NET, "dropped " + describe(m) + ": network not running");
            return false;
        }
        sent.incrementAndGet();
        inFlight.incrementAndGet();
        try {
            current.schedule(() -> arrive(m), nextLatencyMillis(), TimeUnit.MILLISECONDS);
            return true;
        } catch (RejectedExecutionException e) {
            inFlight.decrementAndGet();
            SimLogger.warn(SimLogger.Category.NET, "dropped " + describe(m) + ": network stopped");
            return false;
        }
    }

    /** Desde ahora retiene los mensajes que lleguen. @implNote Called from the thread that pauses. */
    public void onPause() {
        pauseLock.lock();
        try {
            paused = true;
        } finally {
            pauseLock.unlock();
        }
    }

    /** Entrega enseguida los mensajes retenidos. @implNote Called from the thread that resumes. */
    public void onResume() {
        List<Message> release;
        pauseLock.lock();
        try {
            paused = false;
            release = new ArrayList<>(held);
            held.clear();
        } finally {
            pauseLock.unlock();
        }
        ScheduledExecutorService current = executor;
        for (Message m : release) {
            try {
                if (current == null) {
                    throw new RejectedExecutionException("network not running");
                }
                current.execute(() -> deliver(m));
            } catch (RejectedExecutionException e) {
                inFlight.decrementAndGet();
                SimLogger.warn(SimLogger.Category.NET, "dropped " + describe(m) + ": network stopped");
            }
        }
    }

    /** Mensajes retenidos por la pausa. */
    public int heldCount() {
        pauseLock.lock();
        try {
            return held.size();
        } finally {
            pauseLock.unlock();
        }
    }

    /** Mensajes aceptados por {@link #send}. */
    public long sentCount() {
        return sent.get();
    }

    /** Mensajes cuyo handler ya terminó. */
    public long deliveredCount() {
        return delivered.get();
    }

    /** Mensajes enviados que todavía no se entregaron. */
    public long inFlightCount() {
        return inFlight.get();
    }

    private void arrive(Message m) {
        pauseLock.lock();
        try {
            if (paused) {
                held.addLast(m);
                return;
            }
        } finally {
            pauseLock.unlock();
        }
        deliver(m);
    }

    // Un handler que lanza una excepción cuenta como entregado: el mensaje llegó.
    private void deliver(Message m) {
        MessageHandler handler = handlers.get(m.to());
        try {
            if (handler == null) {
                SimLogger.warn(SimLogger.Category.NET, "dropped " + describe(m) + ": destination unregistered");
                return;
            }
            handler.onMessage(m);
        } catch (RuntimeException e) {
            SimLogger.error(SimLogger.Category.NET, "handler of " + m.to() + " failed on " + describe(m) + ": " + e);
        } finally {
            inFlight.decrementAndGet();
            if (handler != null) {
                delivered.incrementAndGet();
            }
        }
    }

    private long nextLatencyMillis() {
        randomLock.lock();
        try {
            return latencyMinMillis + random.nextLong(latencyMaxMillis - latencyMinMillis + 1);
        } finally {
            randomLock.unlock();
        }
    }

    private static String describe(Message m) {
        return m.type() + " " + m.from() + " -> " + m.to() + " (" + m.requestId() + ")";
    }
}
