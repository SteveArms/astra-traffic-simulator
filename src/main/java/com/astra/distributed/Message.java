package com.astra.distributed;

import java.util.Objects;

/**
 * Mensaje entre procesos de Ricart-Agrawala. {@code timestamp} es el valor de Lamport del emisor al enviarlo y
 * {@code requestId} identifica la petición a la que pertenece (un REPLY lleva el de la REQUEST que responde).
 */
public record Message(MessageType type, String from, String to, int timestamp, String requestId) {

    public Message {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(requestId, "requestId");
        if (timestamp < 0) {
            throw new IllegalArgumentException("timestamp must be >= 0: " + timestamp);
        }
    }
}
