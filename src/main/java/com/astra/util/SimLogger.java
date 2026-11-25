package com.astra.util;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * Logger de evidencia de toda la app. Quien registra solo formatea la línea y la encola (sin bloquear); el hilo
 * daemon {@value #THREAD_NAME} la escribe en el archivo y, si se pidió, en la consola.
 *
 * <p>Formato: {@code HH:mm:ss.SSS [CATEGORY] mensaje}, salvo {@link #lamport} ({@code [LAMPORT N] PROCESS -> ACTION})
 * y {@link #ricart} ({@code [RICART] mensaje}), que mantienen el formato de evidencia del informe.
 *
 * <p>Antes de {@link #init} (p. ej. en los tests del motor) escribe solo en consola con nivel INFO.
 *
 * @implNote Todos los métodos son seguros desde cualquier hilo; {@link #init} y {@link #shutdown} los llama el
 *           hilo que arranca y detiene la app.
 */
public final class SimLogger {

    public enum Category { ENGINE, LIGHT, CMD, LAMPORT, RICART, NET, STATS }

    public enum Level { DEBUG, INFO, WARN, ERROR }

    public static final String THREAD_NAME = "astra-logger";

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final long FLUSH_INTERVAL_MILLIS = 200;
    private static final long SHUTDOWN_TIMEOUT_MILLIS = 2_000;
    // Marca de fin para el hilo escritor; se compara por identidad.
    private static final String POISON = new String("<eof>");

    private static final AtomicLongArray COUNTS = new AtomicLongArray(Category.values().length);
    private static final Object LIFECYCLE = new Object();
    private static volatile Sink sink;

    private SimLogger() {
    }

    /**
     * Abre el archivo y arranca el hilo escritor; si ya estaba iniciado, cierra el anterior. Reinicia los contadores.
     */
    public static void init(LogSettings settings) {
        Objects.requireNonNull(settings, "settings");
        synchronized (LIFECYCLE) {
            closeCurrent();
            for (int i = 0; i < COUNTS.length(); i++) {
                COUNTS.set(i, 0);
            }
            sink = new Sink(Path.of(settings.logFile()), settings.logConsole(), settings.logLevel());
        }
    }

    /** Vacía la cola y cierra el archivo, esperando como máximo 2 s. Después vuelve al modo solo consola. */
    public static void shutdown() {
        synchronized (LIFECYCLE) {
            closeCurrent();
        }
    }

    public static void log(Category category, Level level, String message) {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(level, "level");
        Sink current = current();
        if (level.compareTo(current.level) < 0) {
            return;
        }
        String line = LocalTime.now().format(TIME) + " [" + category + "] " + message;
        current.enqueue(category, line);
    }

    public static void info(Category category, String message) {
        log(category, Level.INFO, message);
    }

    public static void warn(Category category, String message) {
        log(category, Level.WARN, message);
    }

    public static void error(Category category, String message) {
        log(category, Level.ERROR, message);
    }

    /** Escribe {@code [LAMPORT clock] process -> action}. */
    public static void lamport(int clock, String process, String action) {
        current().enqueue(Category.LAMPORT, "[LAMPORT " + clock + "] " + process + " -> " + action);
    }

    /** Escribe {@code [RICART] msg}. */
    public static void ricart(String msg) {
        current().enqueue(Category.RICART, "[RICART] " + msg);
    }

    /** Líneas aceptadas de una categoría desde el último {@link #init}. */
    public static long lineCount(Category category) {
        return COUNTS.get(category.ordinal());
    }

    /** Líneas encoladas que el hilo escritor todavía no tomó. */
    public static int pendingCount() {
        Sink current = sink;
        return current == null ? 0 : current.queue.size();
    }

    private static Sink current() {
        Sink current = sink;
        if (current != null) {
            return current;
        }
        synchronized (LIFECYCLE) {
            if (sink == null) {
                sink = new Sink(null, true, Level.INFO);
            }
            return sink;
        }
    }

    private static void closeCurrent() {
        Sink current = sink;
        sink = null;
        if (current != null) {
            current.close();
        }
    }

    private static final class Sink {

        private final LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>();
        private final Path file;
        private final boolean console;
        private final Level level;
        private final Thread writer;

        Sink(Path file, boolean console, Level level) {
            this.file = file;
            this.console = console;
            this.level = level;
            this.writer = new Thread(this::run, THREAD_NAME);
            writer.setDaemon(true);
            writer.start();
        }

        void enqueue(Category category, String line) {
            COUNTS.incrementAndGet(category.ordinal());
            queue.offer(line);
        }

        void close() {
            queue.offer(POISON);
            try {
                writer.join(SHUTDOWN_TIMEOUT_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (writer.isAlive()) {
                writer.interrupt();
            }
        }

        private void run() {
            BufferedWriter out = open();
            try {
                while (true) {
                    String line = queue.poll(FLUSH_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
                    if (line == null) {
                        flush(out);
                        continue;
                    }
                    if (line == POISON) {
                        break;
                    }
                    write(out, line);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                closeQuietly(out);
            }
        }

        private BufferedWriter open() {
            if (file == null) {
                return null;
            }
            try {
                Path parent = file.toAbsolutePath().getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                return Files.newBufferedWriter(file, StandardCharsets.UTF_8);
            } catch (IOException e) {
                stderr().println("[" + THREAD_NAME + "] cannot open " + file + ": " + e);
                return null;
            }
        }

        private void write(BufferedWriter out, String line) {
            if (console) {
                stdout().println(line);
            }
            if (out != null) {
                try {
                    out.write(line);
                    out.newLine();
                } catch (IOException e) {
                    stderr().println("[" + THREAD_NAME + "] write failed: " + e);
                }
            }
        }

        private static void flush(BufferedWriter out) {
            if (out != null) {
                try {
                    out.flush();
                } catch (IOException ignored) {
                    // Se reintenta en el próximo flush.
                }
            }
        }

        private static void closeQuietly(BufferedWriter out) {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ignored) {
                    // No queda nada que hacer al cerrar.
                }
            }
        }

        // Únicos puntos de salida a la consola de la app: solo los usa el hilo escritor.
        private static PrintStream stdout() {
            return System.out;
        }

        private static PrintStream stderr() {
            return System.err;
        }
    }
}
