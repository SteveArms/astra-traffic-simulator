/**
 * Vehículos que recorren el grafo vial: la clase abstracta {@link com.astra.model.Vehicle} y sus tipos
 * {@code Car}, {@code Bus} y {@code Ambulance}, su especificación física ({@link com.astra.model.VehicleSpec}), la
 * fábrica que les calcula la ruta inicial y la vista inmutable que va en el snapshot. Un viaje puede tener varios
 * tramos ({@link com.astra.model.TripPurpose#PARK}). Solo depende de {@code graph} y {@code routing}; recibe las
 * especificaciones por {@link com.astra.model.VehicleSpecs}.
 */
package com.astra.model;
