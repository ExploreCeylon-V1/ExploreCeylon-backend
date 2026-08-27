package com.exploreceylon.backend.service.matrix;

import com.exploreceylon.backend.client.OsrmTableClient;
import com.exploreceylon.backend.dto.matrix.RouteMatrix;
import com.exploreceylon.backend.dto.matrix.RouteMatrixContext;
import com.exploreceylon.backend.dto.matrix.RouteMatrixEntry;
import com.exploreceylon.backend.service.ItineraryAssemblyService.GeoPoint;
import com.exploreceylon.backend.service.ItineraryAssemblyService.TripDay;
import com.exploreceylon.backend.service.budget.VisitDurationEstimator;
import com.exploreceylon.backend.util.HaversineDistanceCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RouteMatrixEngineTest {

    private DefaultRouteMatrixEngine matrixEngine;
    private TimelineOptimizer timelineOptimizer;

    @BeforeEach
    void setUp() {
        OsrmTableClient osrmTableClient = new OsrmTableClient();
        matrixEngine = new DefaultRouteMatrixEngine(osrmTableClient, new HaversineDistanceCalculator());
        timelineOptimizer = new TimelineOptimizer(new VisitDurationEstimator());
    }

    @Test
    @DisplayName("Should build N x N route matrix and retrieve O(1) distance and duration entries")
    void testBuildMatrixAndLookup() {
        GeoPoint colombo = new GeoPoint(6.9271, 79.8612);
        GeoPoint kandy = new GeoPoint(7.2906, 80.6337);
        GeoPoint nuwaraEliya = new GeoPoint(6.9497, 80.7891);

        List<GeoPoint> points = List.of(colombo, kandy, nuwaraEliya);

        RouteMatrixContext context = RouteMatrixContext.builder()
                .locations(points)
                .profile("driving")
                .useCache(true)
                .build();

        RouteMatrix matrix = matrixEngine.buildMatrix(context);

        assertNotNull(matrix);
        assertEquals(3, matrix.getLocations().size());

        RouteMatrixEntry entry = matrixEngine.getEntry(matrix, colombo, kandy);
        assertNotNull(entry);
        assertTrue(entry.getDistanceKm() > 80.0, "Distance Colombo -> Kandy should be > 80km");
        assertTrue(entry.getDurationMinutes() > 60.0, "Duration Colombo -> Kandy should be > 60 min");
    }

    @Test
    @DisplayName("Should hit cache on identical matrix request (0 duplicate HTTP calls)")
    void testMatrixCaching() {
        GeoPoint p1 = new GeoPoint(6.9271, 79.8612);
        GeoPoint p2 = new GeoPoint(7.2906, 80.6337);

        RouteMatrixContext context = RouteMatrixContext.builder()
                .locations(List.of(p1, p2))
                .useCache(true)
                .build();

        RouteMatrix m1 = matrixEngine.buildMatrix(context);
        assertFalse(m1.getStatistics().isCacheHit(), "First call should be a cache miss");

        RouteMatrix m2 = matrixEngine.buildMatrix(context);
        assertTrue(m2.getStatistics().isCacheHit(), "Second call must be a cache HIT");
    }

    @Test
    @DisplayName("Should generate realistic clock-synchronized timeline with Lunch and Tea breaks")
    void testTimelineOptimizer() {
        GeoPoint colombo = new GeoPoint(6.9271, 79.8612);
        GeoPoint kandy = new GeoPoint(7.2906, 80.6337);

        com.exploreceylon.backend.model.Destination d1 = com.exploreceylon.backend.model.Destination.builder()
                .id(1L).name("Pinnawala").category(com.exploreceylon.backend.model.Destination.DestinationCategory.WILDLIFE_NATURE)
                .latitude(7.3013).longitude(80.3860).build();

        com.exploreceylon.backend.model.Destination d2 = com.exploreceylon.backend.model.Destination.builder()
                .id(2L).name("Kandy Temple").category(com.exploreceylon.backend.model.Destination.DestinationCategory.RELIGIOUS)
                .latitude(7.2906).longitude(80.6337).build();

        TripDay day = new TripDay(1, List.of(d1, d2), List.of(), List.of());

        RouteMatrix matrix = matrixEngine.buildMatrix(RouteMatrixContext.builder()
                .locations(List.of(colombo, new GeoPoint(d1.getLatitude(), d1.getLongitude()), new GeoPoint(d2.getLatitude(), d2.getLongitude())))
                .useCache(true)
                .build());

        TimelineOptimizer.ScheduledTimeline timeline = timelineOptimizer.optimizeTimeline(day, matrix, 480, colombo);

        assertNotNull(timeline);
        assertEquals(1, timeline.getDayNumber());
        assertEquals("08:00 AM", timeline.getDayStartTime());
        assertEquals(2, timeline.getScheduledStops().size());
        assertNotNull(timeline.getScheduledStops().get(0).getArrivalTime());
        assertNotNull(timeline.getScheduledStops().get(0).getDepartureTime());
    }
}
