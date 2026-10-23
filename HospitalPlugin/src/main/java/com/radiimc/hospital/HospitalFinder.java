package com.radiimc.hospital;

import java.util.Collection;
import java.util.Optional;

final class HospitalFinder {

    private HospitalFinder() {
    }

    /** The closest hospital in the same world within {@code radius} blocks of the given point. */
    static Optional<Hospital> closest(Collection<Hospital> hospitals, String world,
                                      double x, double y, double z, double radius) {
        Hospital best = null;
        double bestDistanceSq = radius * radius;
        for (Hospital h : hospitals) {
            // Distance across worlds is meaningless (and Location#distance throws), so only compare like with like.
            if (!h.world().equals(world)) {
                continue;
            }
            double dx = h.x() - x, dy = h.y() - y, dz = h.z() - z;
            double distanceSq = dx * dx + dy * dy + dz * dz;
            if (distanceSq <= bestDistanceSq) {
                bestDistanceSq = distanceSq;
                best = h;
            }
        }
        return Optional.ofNullable(best);
    }
}
