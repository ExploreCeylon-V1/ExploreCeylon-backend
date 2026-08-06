package com.exploreceylon.backend.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HaversineDistanceCalculatorTest {

    private final HaversineDistanceCalculator calculator = new HaversineDistanceCalculator();

    @Test
    @DisplayName("Should calculate accurate distance between Colombo and Kandy (~95 km)")
    void testCalculateDistanceKm_ColomboToKandy() {
        // Colombo: 6.9271, 79.8612
        // Kandy: 7.2906, 80.6337
        double distance = calculator.calculateDistanceKm(6.9271, 79.8612, 7.2906, 80.6337);

        assertTrue(distance > 90.0 && distance < 105.0, "Distance Colombo-Kandy should be ~95 km");
    }

    @Test
    @DisplayName("Should return 0.0 for identical coordinates")
    void testCalculateDistanceKm_SameLocation() {
        double distance = calculator.calculateDistanceKm(6.9271, 79.8612, 6.9271, 79.8612);

        assertEquals(0.0, distance, 0.001);
    }
}
