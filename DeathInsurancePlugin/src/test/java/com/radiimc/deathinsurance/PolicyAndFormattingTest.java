package com.radiimc.deathinsurance;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PolicyAndFormattingTest {

    private static final long WEEK = Duration.ofDays(7).toMillis();
    private static final long NOW = 1_000_000_000L;

    @Test
    void firstPolicyStartsNow() {
        assertEquals(NOW + WEEK, PolicyStore.extendedExpiry(NOW, 0, WEEK));
    }

    @Test
    void buyingWhileCoveredStacksOnRemainingTime() {
        long current = NOW + Duration.ofDays(2).toMillis();
        assertEquals(current + WEEK, PolicyStore.extendedExpiry(NOW, current, WEEK));
    }

    @Test
    void lapsedPolicyRestartsFromNow() {
        assertEquals(NOW + WEEK, PolicyStore.extendedExpiry(NOW, NOW - 5_000, WEEK));
    }

    @Test
    void formatsDurations() {
        assertEquals("7d", DeathInsurancePlugin.formatDuration(WEEK));
        assertEquals("6d 23h 59m", DeathInsurancePlugin.formatDuration(WEEK - 1_000));
        assertEquals("5m", DeathInsurancePlugin.formatDuration(Duration.ofMinutes(5).toMillis()));
        assertEquals("4m 30s", DeathInsurancePlugin.formatDuration(Duration.ofSeconds(270).toMillis()));
        assertEquals("0s", DeathInsurancePlugin.formatDuration(-10));
    }
}
