package com.exploreceylon.backend.service.timeline;

import com.exploreceylon.backend.dto.matrix.RouteMatrix;
import com.exploreceylon.backend.dto.matrix.RouteMatrixContext;
import com.exploreceylon.backend.dto.timeline.TimelineContext;
import com.exploreceylon.backend.dto.timeline.TimelineQualityScore;
import com.exploreceylon.backend.dto.timeline.TimelineStop;
import com.exploreceylon.backend.dto.timeline.TimelineValidationResult;
import com.exploreceylon.backend.model.Destination;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AttractionScheduleEngineTest {

    private DefaultAttractionScheduleEngine scheduleEngine;
    private TimelineValidationService validationService;
    private DefaultRouteMatrixEngine matrixEngine;

    @BeforeEach
    void setUp() {
        OpeningHoursService openingHoursService = new OpeningHoursService();
        PreferredVisitWindowService windowService = new PreferredVisitWindowService();
        VisitDurationEstimator durationEstimator = new VisitDurationEstimator();

        scheduleEngine = new DefaultAttractionScheduleEngine(openingHoursService, windowService, durationEstimator);
        validationService = new TimelineValidationService(openingHoursService, windowService);
        matrixEngine = new DefaultRouteMatrixEngine(new OsrmTableClient(), new HaversineDistanceCalculator());
    }

    @Test
    @DisplayName("Should schedule attractions with opening hours, waiting logic, and meal breaks")
    void testScheduleDayTimeline() {
        GeoPoint colombo = new GeoPoint(6.9271, 79.8612);

        Destination d1 = Destination.builder().id(1L).name("Pinnawala Elephant Orphanage").category(DestinationCategory.WILDLIFE_NATURE).latitude(7.3013).longitude(80.3860).build();
        Destination d2 = Destination.builder().id(2L).name("Kandy Temple of Tooth").category(DestinationCategory.RELIGIOUS).latitude(7.2906).longitude(80.6337).build();

        TripDay tripDay = new TripDay(1, List.of(d1, d2), List.of(), List.of());

        RouteMatrix matrix = matrixEngine.buildMatrix(RouteMatrixContext.builder()
                .locations(List.of(colombo, new GeoPoint(d1.getLatitude(), d1.getLongitude()), new GeoPoint(d2.getLatitude(), d2.getLongitude())))
                .useCache(true)
                .build());

        TimelineContext context = TimelineContext.builder()
                .tripDay(tripDay)
                .routeMatrix(matrix)
                .dayStartMinutes(480) // 08:00 AM
                .originPoint(colombo)
                .build();

        List<TimelineStop> stops = scheduleEngine.scheduleDayTimeline(context);

        assertNotNull(stops);
        assertEquals(2, stops.size());
        assertNotNull(stops.get(0).getVisitStartTime());
        assertNotNull(stops.get(0).getVisitEndTime());

        TimelineValidationResult validation = validationService.validateTimeline(stops);
        assertNotNull(validation);
        assertTrue(validation.getQualityScore() > 50.0, "Quality score should be positive and high");

        TimelineQualityScore score = validationService.computeQualityScore(stops);
        assertTrue(score.getTotalScore() > 0.0);
    }
}
