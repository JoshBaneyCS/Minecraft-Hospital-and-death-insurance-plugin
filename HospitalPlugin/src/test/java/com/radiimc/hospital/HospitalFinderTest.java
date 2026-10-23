package com.radiimc.hospital;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HospitalFinderTest {

    private static Hospital at(String name, String world, double x, double z) {
        return new Hospital(name, world, x, 64, z, 0, 0);
    }

    @Test
    void picksTheClosestHospitalInTheSameWorld() {
        List<Hospital> hospitals = List.of(at("far", "world", 400, 0), at("near", "world", 50, 0), at("mid", "world", 0, 200));
        assertEquals("near", HospitalFinder.closest(hospitals, "world", 0, 64, 0, 500).orElseThrow().name());
    }

    @Test
    void ignoresHospitalsInOtherWorlds() {
        List<Hospital> hospitals = List.of(at("nether", "world_nether", 1, 0), at("overworld", "world", 300, 0));
        assertEquals("overworld", HospitalFinder.closest(hospitals, "world", 0, 64, 0, 500).orElseThrow().name());
        assertTrue(HospitalFinder.closest(hospitals, "world_the_end", 0, 64, 0, 500).isEmpty());
    }

    @Test
    void radiusIsInclusiveAndAnythingBeyondItIsSkipped() {
        List<Hospital> hospitals = List.of(at("edge", "world", 500, 0), at("outside", "world", 501, 0));
        assertEquals("edge", HospitalFinder.closest(hospitals, "world", 0, 64, 0, 500).orElseThrow().name());
        assertTrue(HospitalFinder.closest(List.of(at("outside", "world", 501, 0)), "world", 0, 64, 0, 500).isEmpty());
    }
}
