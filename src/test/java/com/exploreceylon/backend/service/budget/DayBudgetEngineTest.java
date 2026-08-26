package com.exploreceylon.backend.service.budget;

import com.exploreceylon.backend.dto.budget.DayBudget;
import com.exploreceylon.backend.dto.budget.DayBudgetContext;
import com.exploreceylon.backend.model.Destination.DestinationCategory;
import com.exploreceylon.backend.service.ItineraryAssemblyService.GeoPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DayBudgetEngineTest {

    private VisitDurationEstimator durationEstimator;
    private DefaultDayBudgetEngine dayBudgetEngine;

    @BeforeEach
    void setUp() {
        durationEstimator = new VisitDurationEstimator();
        dayBudgetEngine = new DefaultDayBudgetEngine(durationEstimator);
    }

    @Test
    @DisplayName("Should estimate visit durations accurately based on destination categories")
    void testVisitDurationEstimator() {
        assertEquals(45, durationEstimator.estimateMinutes(DestinationCategory.RELIGIOUS));
        assertEquals(60, durationEstimator.estimateMinutes(DestinationCategory.CULTURE_HERITAGE));
        assertEquals(90, durationEstimator.estimateMinutes(DestinationCategory.BEACH_COAST));
        assertEquals(180, durationEstimator.estimateMinutes(DestinationCategory.WILDLIFE_NATURE));
        assertEquals(150, durationEstimator.estimateMinutes(DestinationCategory.ADVENTURE));
    }

    @Test
    @DisplayName("Should calculate accurate budgets for Colombo -> Nuwara Eliya (3 Days, Balanced)")
    void testColomboToNuwaraEliya_3Days_Balanced() {
        DayBudgetContext context = DayBudgetContext.builder()
                .tripDurationDays(3)
                .travelStyle("BALANCED")
                .totalRouteDistanceKm(165.8)
                .totalRouteDurationMinutes(278)
                .origin(new GeoPoint(6.9271, 79.8612))
                .destination(new GeoPoint(6.9497, 80.7891))
                .build();

        List<DayBudget> budgets = dayBudgetEngine.calculateTripDayBudgets(context);

        assertEquals(3, budgets.size());
        DayBudget day1 = budgets.get(0);

        assertEquals(1, day1.getDayNumber());
        assertEquals(390, day1.getAvailableSightseeingMinutes(), "Balanced style allows 390 min (6.5h) sightseeing");
        assertEquals(92, day1.getAvailableDrivingMinutes(), "Target driving duration per day ~92 min (278 / 3)");
        assertEquals(140, day1.getReservedMealMinutes(), "Reserved meal time must be 140 min");
        assertEquals(45, day1.getReservedBufferMinutes(), "Traffic/delay buffer must be 45 min");
        assertEquals(55.27, day1.getDrivingDistanceTargetKm(), 0.1, "Driving distance target per day ~55.27 km");
        assertEquals(6, day1.getMaximumVisitCount(), "Maximum visit count floor(390 / 60) = 6");
    }

    @Test
    @DisplayName("Should calculate accurate budgets for Colombo -> Kandy (2 Days, Relaxed)")
    void testColomboToKandy_2Days_Relaxed() {
        DayBudgetContext context = DayBudgetContext.builder()
                .tripDurationDays(2)
                .travelStyle("RELAXED")
                .totalRouteDistanceKm(115.2)
                .totalRouteDurationMinutes(192)
                .origin(new GeoPoint(6.9271, 79.8612))
                .destination(new GeoPoint(7.2906, 80.6337))
                .build();

        List<DayBudget> budgets = dayBudgetEngine.calculateTripDayBudgets(context);

        assertEquals(2, budgets.size());
        DayBudget day1 = budgets.get(0);

        assertEquals(300, day1.getAvailableSightseeingMinutes(), "Relaxed style allows 300 min (5h) sightseeing");
        assertEquals(96, day1.getAvailableDrivingMinutes(), "Target driving duration per day ~96 min");
        assertEquals(57.6, day1.getDrivingDistanceTargetKm(), 0.1);
        assertEquals(5, day1.getMaximumVisitCount(), "Maximum visit count floor(300 / 60) = 5");
    }

    @Test
    @DisplayName("Should calculate accurate budgets for Galle -> Matara (2 Days, Fast-paced)")
    void testGalleToMatara_2Days_FastPaced() {
        DayBudgetContext context = DayBudgetContext.builder()
                .tripDurationDays(2)
                .travelStyle("FAST_PACED")
                .totalRouteDistanceKm(44.1)
                .totalRouteDurationMinutes(48)
                .origin(new GeoPoint(6.0535, 80.2210))
                .destination(new GeoPoint(5.9549, 80.5550))
                .build();

        List<DayBudget> budgets = dayBudgetEngine.calculateTripDayBudgets(context);

        assertEquals(2, budgets.size());
        DayBudget day1 = budgets.get(0);

        assertEquals(480, day1.getAvailableSightseeingMinutes(), "Fast-paced style allows 480 min (8h) sightseeing");
        assertEquals(30, day1.getAvailableDrivingMinutes(), "Minimum floor driving time 30 min");
        assertEquals(22.05, day1.getDrivingDistanceTargetKm(), 0.1);
        assertEquals(8, day1.getMaximumVisitCount(), "Maximum visit count floor(480 / 60) = 8");
    }
}
