package com.exploreceylon.backend.service.planner;

import com.exploreceylon.backend.client.OsrmTableClient;
import com.exploreceylon.backend.dto.planner.PlannerRequest;
import com.exploreceylon.backend.dto.planner.PlannerResponse;
import com.exploreceylon.backend.service.ItineraryAssemblyService;
import com.exploreceylon.backend.service.ItineraryAssemblyService.GeoPoint;
import com.exploreceylon.backend.service.ItineraryAssemblyService.PlannedDay;
import com.exploreceylon.backend.service.budget.VisitDurationEstimator;
import com.exploreceylon.backend.service.cost.DefaultTripCostEngine;
import com.exploreceylon.backend.service.matrix.DefaultRouteMatrixEngine;
import com.exploreceylon.backend.service.narrative.DefaultNarrativeGenerationService;
import com.exploreceylon.backend.service.narrative.NarrativePromptBuilder;
import com.exploreceylon.backend.service.recommendation.DefaultGemRecommendationEngine;
import com.exploreceylon.backend.service.timeline.DefaultAttractionScheduleEngine;
import com.exploreceylon.backend.service.timeline.OpeningHoursService;
import com.exploreceylon.backend.service.timeline.PreferredVisitWindowService;
import com.exploreceylon.backend.util.DistanceCalculator;
import com.exploreceylon.backend.util.HaversineDistanceCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

class PlannerFacadeServiceTest {

    private DefaultPlannerFacadeService plannerFacadeService;

    @BeforeEach
    void setUp() {
        ItineraryAssemblyService assemblyService = Mockito.mock(ItineraryAssemblyService.class);
        DistanceCalculator distanceCalculator = new HaversineDistanceCalculator();

        DefaultRouteMatrixEngine matrixEngine = new DefaultRouteMatrixEngine(new OsrmTableClient(), distanceCalculator);
        OpeningHoursService openingHoursService = new OpeningHoursService();
        PreferredVisitWindowService windowService = new PreferredVisitWindowService();
        VisitDurationEstimator durationEstimator = new VisitDurationEstimator();

        DefaultAttractionScheduleEngine scheduleEngine = new DefaultAttractionScheduleEngine(openingHoursService, windowService, durationEstimator);
        DefaultGemRecommendationEngine gemEngine = new DefaultGemRecommendationEngine(durationEstimator);
        DefaultNarrativeGenerationService narrativeService = new DefaultNarrativeGenerationService(new NarrativePromptBuilder());
        DefaultTripCostEngine costEngine = new DefaultTripCostEngine();

        plannerFacadeService = new DefaultPlannerFacadeService(
                assemblyService, matrixEngine, scheduleEngine, gemEngine, narrativeService, costEngine
        );

        when(assemblyService.geocode("Colombo")).thenReturn(Optional.of(new GeoPoint(6.9271, 79.8612)));
        when(assemblyService.geocode("Kandy")).thenReturn(Optional.of(new GeoPoint(7.2906, 80.6337)));
        when(assemblyService.assemble(any(), any(), any(), anyInt(), anyInt(), any(), any()))
                .thenReturn(List.of(new PlannedDay(1, LocalDate.of(2026, 9, 1), "Colombo", List.of(), 50.0)));
        when(assemblyService.assemble(any(), any(), any(), anyInt(), anyInt(), any(), any(), org.mockito.ArgumentMatchers.nullable(String.class)))
                .thenReturn(List.of(new PlannedDay(1, LocalDate.of(2026, 9, 1), "Colombo", List.of(), 50.0)));
    }

    @Test
    @DisplayName("Should execute complete 11-phase planner pipeline and return production-ready PlannerResponse")
    void testGenerateItineraryFacade() {
        PlannerRequest request = PlannerRequest.builder()
                .origin("Colombo")
                .destination("Kandy")
                .tripDays(2)
                .budget("MID_RANGE")
                .travelStyle("RELAXED")
                .groupSize(2)
                .startDate(LocalDate.of(2026, 9, 1))
                .build();

        PlannerResponse response = plannerFacadeService.generateItinerary(request);

        assertNotNull(response);
        assertNotNull(response.getSummary());
        assertEquals("Colombo", response.getSummary().getOrigin());
        assertEquals("Kandy", response.getSummary().getDestination());
        assertEquals(2, response.getSummary().getTripDays());
        assertNotNull(response.getEstimatedCost());
        assertNotNull(response.getNarrative());
        assertNotNull(response.getStatistics());
        assertEquals(100.0, response.getStatistics().getRouteMatrixReusePercentage());
    }

    @Test
    @DisplayName("Should extract full multi-select styles from preferences and pass all downstream")
    void testGenerateItineraryWithMultiSelectPreferences() {
        PlannerRequest request = PlannerRequest.builder()
                .origin("Colombo")
                .destination("Kandy")
                .tripDays(3)
                .budget("MID_RANGE")
                .travelStyle("ADVENTURE")
                .preferences(List.of("ADVENTURE", "CULTURAL", "WILDLIFE"))
                .groupSize(2)
                .startDate(LocalDate.of(2026, 9, 1))
                .build();

        PlannerResponse response = plannerFacadeService.generateItinerary(request);

        assertNotNull(response);
        assertEquals("ADVENTURE, CULTURAL, WILDLIFE", response.getSummary().getTravelStyle());
    }

    @Test
    @DisplayName("Should parse comma-separated travelStyle when preferences is null or empty")
    void testGenerateItineraryWithCommaSeparatedTravelStyle() {
        PlannerRequest request = PlannerRequest.builder()
                .origin("Colombo")
                .destination("Kandy")
                .tripDays(2)
                .budget("MID_RANGE")
                .travelStyle("ADVENTURE, CULTURAL")
                .groupSize(2)
                .startDate(LocalDate.of(2026, 9, 1))
                .build();

        PlannerResponse response = plannerFacadeService.generateItinerary(request);

        assertNotNull(response);
        assertEquals("ADVENTURE, CULTURAL", response.getSummary().getTravelStyle());
    }

    @Test
    @DisplayName("Should fallback to BALANCED when preferences and travelStyle are both absent")
    void testGenerateItineraryWithBalancedFallback() {
        PlannerRequest request = PlannerRequest.builder()
                .origin("Colombo")
                .destination("Kandy")
                .tripDays(2)
                .budget("MID_RANGE")
                .travelStyle(null)
                .preferences(null)
                .groupSize(2)
                .startDate(LocalDate.of(2026, 9, 1))
                .build();

        PlannerResponse response = plannerFacadeService.generateItinerary(request);

        assertNotNull(response);
        assertEquals("BALANCED", response.getSummary().getTravelStyle());
    }
}
