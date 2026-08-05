package com.exploreceylon.backend.util;

import com.exploreceylon.backend.client.OsrmClient;
import com.exploreceylon.backend.dto.routing.DistanceResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OsrmDistanceCalculatorTest {

    private OsrmDistanceCalculator osrmCalculator;
    private HaversineDistanceCalculator haversineCalculator;

    @BeforeEach
    void setUp() {
        haversineCalculator = new HaversineDistanceCalculator();
        OsrmClient osrmClient = new OsrmClient();
        osrmCalculator = new OsrmDistanceCalculator(osrmClient, haversineCalculator);
    }

    @Test
    @DisplayName("Should compare Haversine vs OSRM for Colombo -> Kandy")
    void testColomboToKandy() {
        // Colombo: 6.9271, 79.8612
        // Kandy: 7.2906, 80.6337
        DistanceResult havResult = haversineCalculator.calculateRoute(6.9271, 79.8612, 7.2906, 80.6337);
        DistanceResult osrmResult = osrmCalculator.calculateRoute(6.9271, 79.8612, 7.2906, 80.6337);

        assertNotNull(osrmResult);
        assertTrue(osrmResult.getDrivingDistanceKm() > 0.0);
        assertTrue(osrmResult.getDrivingDurationMinutes() > 0);
        System.out.printf("Colombo -> Kandy | HAVERSINE: %.2f km (%d min) | %s: %.2f km (%d min)%n",
                havResult.getDrivingDistanceKm(), havResult.getDrivingDurationMinutes(),
                osrmResult.getProviderUsed(), osrmResult.getDrivingDistanceKm(), osrmResult.getDrivingDurationMinutes());
    }

    @Test
    @DisplayName("Should test caching mechanism for repeated queries")
    void testCacheMechanism() {
        assertEquals(0, osrmCalculator.getCacheSize());
        DistanceResult res1 = osrmCalculator.calculateRoute(6.0535, 80.2210, 5.9549, 80.5550); // Galle -> Matara
        assertEquals(1, osrmCalculator.getCacheSize());

        DistanceResult res2 = osrmCalculator.calculateRoute(6.0535, 80.2210, 5.9549, 80.5550);
        assertEquals(1, osrmCalculator.getCacheSize(), "Second lookup should hit cache");
        assertEquals(res1.getDrivingDistanceKm(), res2.getDrivingDistanceKm());
    }

    @Test
    @DisplayName("Should fallback gracefully to Haversine if OSRM client fails")
    void testFallbackOnFailure() {
        OsrmClient failingClient = new OsrmClient() {
            @Override
            public DistanceResult getRoute(double lat1, double lng1, double lat2, double lng2) {
                return null; // simulate API timeout/failure
            }
        };

        OsrmDistanceCalculator calcWithFallback = new OsrmDistanceCalculator(failingClient, haversineCalculator);
        DistanceResult result = calcWithFallback.calculateRoute(6.9271, 79.8612, 7.2906, 80.6337);

        assertNotNull(result);
        assertEquals("HAVERSINE", result.getProviderUsed());
        assertTrue(result.isSuccess());
    }
}
