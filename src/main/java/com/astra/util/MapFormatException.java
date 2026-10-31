package com.astra.util;

/** El archivo de mapa no existe, no es JSON válido o tiene datos inconsistentes. */
public final class MapFormatException extends RuntimeException {

    public MapFormatException(String message) {
        super(message);
    }

    public MapFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
