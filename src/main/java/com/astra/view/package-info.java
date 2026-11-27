/**
 * Vista JavaFX: {@link com.astra.view.CityRenderer} dibuja el {@code WorldSnapshot} en dos {@code Canvas} (capa
 * estática y dinámica), con {@code CityPainter}, {@code SignalPainter} y {@code VehiclePainter};
 * {@code LotGeometry} calcula sin JavaFX los cajones de cada playa, para que las dos capas coincidan.
 * {@link com.astra.view.ControlPanel} y {@link com.astra.view.StatsPanel} son los paneles laterales. Nunca modifica el
 * mundo: cada acción es un {@code Command}. Todo corre en el FX Application Thread.
 */
package com.astra.view;
