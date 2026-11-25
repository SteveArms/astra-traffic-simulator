package com.astra.distributed;

/**
 * Recibe los mensajes dirigidos a un proceso.
 *
 * @implNote Called from the astra-net thread only: todos los handlers corren en ese único hilo.
 */
@FunctionalInterface
public interface MessageHandler {

    void onMessage(Message m);
}
