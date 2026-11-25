package com.astra.parking;

import com.astra.graph.Intersection;
import java.util.Objects;

/** Qué hace un vehículo que encontró la playa llena o cerrada. */
public sealed interface SearchDecision {

    /** Ir a otra playa. */
    record Reroute(ParkingLot target) implements SearchDecision {
        public Reroute {
            Objects.requireNonNull(target, "target");
        }
    }

    /** Esperar detenido en la entrada hasta {@code seconds} de tiempo simulado; si se libera un cajón, entra. */
    record Wait(double seconds) implements SearchDecision {
        public Wait {
            if (!(seconds >= 0)) {
                throw new IllegalArgumentException("seconds must be >= 0: " + seconds);
            }
        }
    }

    /** Rendirse y salir de la ciudad por {@code gateway}. */
    record GiveUp(Intersection gateway) implements SearchDecision {
        public GiveUp {
            Objects.requireNonNull(gateway, "gateway");
        }
    }
}
