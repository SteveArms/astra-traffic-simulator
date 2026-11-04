package com.astra.model;

/** Especificación por tipo de vehículo; {@code SimConfig} la implementa para que {@code model} no dependa de {@code core}. */
public interface VehicleSpecs {

    VehicleSpec specFor(VehicleKind kind);
}
