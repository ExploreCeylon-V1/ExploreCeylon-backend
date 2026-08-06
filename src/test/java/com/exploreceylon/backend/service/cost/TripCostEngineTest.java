package com.exploreceylon.backend.service.cost;

import com.exploreceylon.backend.dto.cost.TripCostEstimate;
import com.exploreceylon.backend.dto.matrix.RouteMatrix;
import com.exploreceylon.backend.dto.matrix.RouteMatrixContext;
import com.exploreceylon.backend.client.OsrmTableClient;
import com.exploreceylon.backend.service.ItineraryAssemblyService.GeoPoint;
import com.exploreceylon.backend.service.ItineraryAssemblyService.PlannedDay;
import com.exploreceylon.backend.service.ItineraryAssemblyService.PlannedStop;
import com.exploreceylon.backend.service.ItineraryAssemblyService.StopType;
import com.exploreceylon.backend.service.matrix.DefaultRouteMatrixEngine;
import com.exploreceylon.backend.util.HaversineDistanceCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TripCostEngineTest {

    private DefaultTripCostEngine costEngine;
    private DefaultRouteMatrixEngine matrixEngine;

    @BeforeEach
    void setUp() {
        costEngine = new DefaultTripCostEngine();
        matrixEngine = new DefaultRouteMatrixEngine(new OsrmTableClient(), new HaversineDistanceCalculator());
    }

    @Test
    @DisplayName("Should calculate trip cost breakdown and grand total deterministically without external APIs")
    void testTripCostCalculation() {
        GeoPoint colombo = new GeoPoint(6.9271, 79.8612);
        GeoPoint kandy = new GeoPoint(7.2906, 80.6337);

        PlannedStop s1 = new PlannedStop(StopType.DESTINATION, 1L, "Pinnawala Elephant Orphanage", "Kegalle", 7.3013, 80.3860, 60, 20.0, "MORNING");
        PlannedStop s2 = new PlannedStop(StopType.DESTINATION, 2L, "Kandy Temple of Tooth", "Kandy", 7.2906, 80.6337, 60, 15.0, "AFTERNOON");

        PlannedDay day1 = new PlannedDay(1, LocalDate.of(2026, 9, 1), "Kandy", List.of(s1, s2), 100.0);

        RouteMatrix matrix = matrixEngine.buildMatrix(RouteMatrixContext.builder()
                .locations(List.of(colombo, new GeoPoint(s1.lat(), s1.lng()), new GeoPoint(s2.lat(), s2.lng())))
                .useCache(true)
                .build());

        TripCostEstimate estimate = costEngine.estimateTripCost(List.of(day1), matrix, "RELAXED", 2);

        assertNotNull(estimate);
        assertEquals("LKR", estimate.getCurrency());
        assertEquals(1, estimate.getDailyEstimates().size());
        assertTrue(estimate.getGrandTotal() > 0.0);
        assertTrue(estimate.getTotalBreakdown().getTransportCost() > 0.0);
        assertTrue(estimate.getTotalBreakdown().getFoodCost() == 7000.0, "2 travelers * 3500 LKR food = 7000 LKR");
        assertTrue(estimate.getTotalBreakdown().getParkingCost() == 500.0);
        assertTrue(estimate.getTotalBreakdown().getMiscCost() == 1000.0);
    }
}
