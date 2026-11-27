/**
 * Acciones de usuario con el patrón Command. La UI crea los comandos con {@link com.astra.command.Commands} y los
 * envía al {@link com.astra.command.CommandBus} (una {@code LinkedBlockingQueue}); el motor los drena y ejecuta en
 * orden FIFO al inicio de cada tick, en el hilo {@code astra-engine}.
 */
package com.astra.command;
