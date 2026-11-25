package com.astra.util;

/** Parámetros de {@link SimLogger}; {@code SimConfig} la implementa, así {@code util} no depende de {@code core}. */
public interface LogSettings {

    /** Ruta del archivo de log; su carpeta se crea si no existe. */
    String logFile();

    /** {@code true} para copiar cada línea también a la consola. */
    boolean logConsole();

    /** Nivel mínimo que se escribe. */
    SimLogger.Level logLevel();
}
