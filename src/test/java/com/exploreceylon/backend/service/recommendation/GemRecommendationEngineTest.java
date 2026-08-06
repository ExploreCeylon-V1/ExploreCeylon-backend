package com.exploreceylon.backend.service.recommendation;

import com.exploreceylon.backend.dto.matrix.RouteMatrix;
import com.exploreceylon.backend.dto.matrix.RouteMatrixContext;
import com.exploreceylon.backend.dto.recommendation.GemRecommendationContext;
import com.exploreceylon.backend.dto.recommendation.RecommendationStatistics;
import com.exploreceylon.backend.dto.recommendation.RecommendedGem;
import com.exploreceylon.backend.dto.timeline.TimelineStop;
import com.exploreceylon.backend.model.Event;
import com.exploreceylon.backend.model.HiddenGem;
import com.exploreceylon.backend.model.Destination.DestinationCategory;
import com.exploreceylon.backend.service.ItineraryAssemblyService.GeoPoint;
import com.exploreceylon.backend.service.ItineraryAssemblyService.TripDay;
import com.exploreceylon.backend.service.budget.VisitDurationEstimator;
import com.exploreceylon.backend.service.matrix.DefaultRouteMatrixEngine;
import com.exploreceylon.backend.client.OsrmTableClient;
import com.exploreceylon.backend.util.HaversineDistanceCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GemRecommendationEngineTest {

    private DefaultGemRecommendationEngine gemEngine;
    private DefaultRouteMatrixEngine matrixEngine;

    @BeforeEach
    void setUp() {
        VisitDurationEstimator durationEstimator = new VisitDurationEstimator();
        gemEngine = new DefaultGemRecommendationEngine(durationEstimator);
        matrixEngine = new DefaultRouteMatrixEngine(new OsrmTableClient(), new HaversineDistanceCalculator());
    }

    @Test
    @DisplayName("Should recommend top-rated hidden gems and matching seasonal events without duplicate stops")
    void testRecommendGemsAndEvents() {
        GeoPoint colombo = new GeoPoint(6.9271, 79.8612);

        HiddenGem gem1 = HiddenGem.builder().id(101L).title("Bopath Ella Waterfall").category(HiddenGem.GemCategory.WATERFALL).rating(4.6).reviewCount(25).latitude(6.7844).longitude(80.3644).build();
        HiddenGem gem2 = HiddenGem.builder().id(102L).title("Low quality gem").category(HiddenGem.GemCategory.WATERFALL).rating(3.2).reviewCount(2).latitude(6.7000).longitude(80.3000).build();

        Event event1 = Event.builder().id(201L).title("Kandy Esala Perahera").startDate(LocalDate.of(2026, 8, 1)).endDate(LocalDate.of(2026, 8, 15)).latitude(7.2906).longitude(80.6337).build();

        TripDay day = new TripDay(1, List.of(), List.of(), List.of());

        RouteMatrix matrix = matrixEngine.buildMatrix(RouteMatrixContext.builder()
                .locations(List.of(colombo, new GeoPoint(gem1.getLatitude(), gem1.getLongitude())))
                .useCache(true)
                .build());

        GemRecommendationContext context = GemRecommendationContext.builder()
                .tripDay(day)
                .scheduledStops(List.of())
                .routeMatrix(matrix)
                .travelStyle("BALANCED")
                .candidateGems(List.of(gem1, gem2))
                .candidateEvents(List.of(event1))
                .currentDate(LocalDate.of(2026, 8, 5))
                .build();

        List<RecommendedGem> recommendations = gemEngine.recommendGemsAndEvents(context);

        assertNotNull(recommendations);
        assertFalse(recommendations.isEmpty(), "Should contain recommended gems/events");
        assertEquals(2, recommendations.size(), "Should recommend gem1 and event1, filtering out gem2 (rating < 4.0)");

        RecommendationStatistics stats = gemEngine.computeStatistics(context, recommendations);
        assertNotNull(stats);
        assertEquals(1, stats.getRecommendedGemCount());
        assertEquals(1, stats.getRecommendedEventCount());
        assertTrue(stats.getAverageGemRating() >= 4.0);
    }
}
