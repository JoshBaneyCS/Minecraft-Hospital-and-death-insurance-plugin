package com.radiimc.hospital;

/** The hospital a dead player will wake up in, and whether it is the main (fallback) hospital. */
public record HospitalMatch(Hospital hospital, boolean main) {
}
