package com.exploreceylon.backend.service.regeneration;

import com.exploreceylon.backend.client.OsrmTableClient;
import com.exploreceylon.backend.dto.matrix.RouteMatrix;
import com.exploreceylon.backend.dto.matrix.RouteMatrixContext;
import com.exploreceylon.backend.dto.regeneration.RegenerationContext;
import com.exploreceylon.backend.dto.regeneration.TripRegenerationRequest;
import com.exploreceylon.backend.dto.regeneration.TripRegenerationResult;
import com.exploreceylon.backend.service.ItineraryAssemblyService.GeoPoint;
import com.exploreceylon.backend.service.ItineraryAssemblyService.PlannedDay;
import com.exploreceylon.backend.service.ItineraryAssemblyService.PlannedStop;
import com.exploreceylon.backend.service.ItineraryAssemblyService.StopType;
import com.exploreceylon.backend.service.budget.VisitDurationEstimator;
import com.exploreceylon.backend.service.cost.DefaultTripCostEngine;
import com.exploreceylon.backend.service.editable.DefaultEditableTripEngine;
import com.exploreceylon.backend.service.matrix.DefaultRouteMatrixEngine;
import com.exploreceylon.backend.service.narrative.DefaultNarrativeGenerationService;
import com.exploreceylon.backend.service.narrative.NarrativePromptBuilder;
import com.exploreceylon.backend.service.timeline.DefaultAttractionScheduleEngine;
import com.exploreceylon.backend.service.timeline.OpeningHoursService;
import com.exploreceylon.backend.service.timeline.PreferredVisitWindowService;
import com.exploreceylon.backend.service.timeline.TimelineValidationService;
import com.exploreceylon.backend.util.HaversineDistanceCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TripRegenerationEngineTest {

    private DefaultTripRegenerationEngine regenerationEngine;
    private DefaultRouteMatrixEngine matrixEngine;

    @BeforeEach
    void setUp() {
        OpeningHoursService openingHoursService = new OpeningHoursService();
        PreferredVisitWindowService windowService = new PreferredVisitWindowService();
        VisitDurationEstimator durationEstimator = new VisitDurationEstimator();

        TimelineValidationService validationService = new TimelineValidationService(openingHoursService, windowService);
        DefaultAttractionScheduleEngine scheduleEngine = new DefaultAttractionScheduleEngine(openingHoursService, windowService, durationEstimator);
        DefaultEditableTripEngine editableTripEngine = new DefaultEditableTripEngine(validationService, scheduleEngine);
        DefaultNarrativeGenerationService narrativeService = new DefaultNarrativeGenerationService(new NarrativePromptBuilder());
        DefaultTripCostEngine costEngine = new DefaultTripCostEngine();

        regenerationEngine = new DefaultTripRegenerationEngine(editableTripEngine, narrativeService, costEngine);
        matrixEngine = new DefaultRouteMatrixEngine(new OsrmTableClient(), new HaversineDistanceCalculator());
    }

    @Test
    @DisplayName("Should perform localized regeneration on affected Day 1 while keeping Day 2 untouched")
    void testLocalizedRegeneration() {
        GeoPoint colombo = new GeoPoint(6.9271, 79.8612);

        PlannedStop s1 = new PlannedStop(StopType.DESTINATION, 10L, "Pinnawala", "Kegalle", 7.3013, 80.3860, 60, 10.0, "MORNING");
        PlannedStop s2 = new PlannedStop(StopType.DESTINATION, 20L, "Kandy Temple", "Kandy", 7.2906, 80.6337, 60, 15.0, "AFTERNOON");

        PlannedDay day1 = new PlannedDay(1, LocalDate.of(2026, 9, 1), "Kegalle", new ArrayList<>(List.of(s1)), 50.0);
        PlannedDay day2 = new PlannedDay(2, LocalDate.of(2026, 9, 2), "Kandy", new ArrayList<>(List.of(s2)), 60.0);

        RouteMatrix matrix = matrixEngine.buildMatrix(RouteMatrixContext.builder()
                .locations(List.of(colombo, new GeoPoint(s1.lat(), s1.lng()), new GeoPoint(s2.lat(), s2.lng())))
                .useCache(true)
                .build());

        TripRegenerationRequest req = TripRegenerationRequest.builder()
                .regenerationType("DESTINATION_REMOVED")
                .targetDayNumber(1)
                .targetStopId(10L)
                .lockedDayNumbers(Set.of())
                .build();

        RegenerationContext context = RegenerationContext.builder()
                .originalDays(List.of(day1, day2))
                .routeMatrix(matrix)
                .request(req)
                .build();

        TripRegenerationResult result = regenerationEngine.regenerateTrip(context);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(1, result.getAffectedDays().size());
        assertEquals(1, result.getAffectedDays().get(0).getDayNumber());
        assertNotNull(result.getUpdatedNarrative());
        assertNotNull(result.getUpdatedCostEstimate());
        assertEquals(100.0, result.getStatistics().getMatrixReusedPercentage(), "100% matrix reuse");
        assertTrue(result.getStatistics().getExecutionTimeMs() < 100, "Execution should be fast");
    }

    @Test
    @DisplayName("Should skip locked days during regeneration")
    void testLockedDayRegenerationSkip() {
        PlannedStop s1 = new PlannedStop(StopType.DESTINATION, 30L, "Sigiriya", "Matale", 7.9570, 80.7600, 120, 30.0, "MORNING");
        PlannedDay day1 = new PlannedDay(1, LocalDate.of(2026, 9, 1), "Matale", new ArrayList<>(List.of(s1)), 70.0);

        TripRegenerationRequest req = TripRegenerationRequest.builder()
                .regenerationType("DESTINATION_REMOVED")
                .targetDayNumber(1)
                .targetStopId(30L)
                .lockedDayNumbers(Set.of(1)) // Day 1 is LOCKED!
                .build();

        RegenerationContext context = RegenerationContext.builder()
                .originalDays(List.of(day1))
                .request(req)
                .build();

        TripRegenerationResult result = regenerationEngine.regenerateTrip(context);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertTrue(result.getAffectedDays().get(0).isLocked(), "Affected day should be marked as locked");
    }
}
