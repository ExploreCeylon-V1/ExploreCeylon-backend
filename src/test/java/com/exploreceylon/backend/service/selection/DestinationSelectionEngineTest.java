package com.exploreceylon.backend.service.selection;

import com.exploreceylon.backend.dto.budget.DayBudget;
import com.exploreceylon.backend.dto.selection.SelectionContext;
import com.exploreceylon.backend.dto.selection.SelectionStatistics;
import com.exploreceylon.backend.model.Destination;
import com.exploreceylon.backend.model.Destination.DestinationCategory;
import com.exploreceylon.backend.service.ItineraryAssemblyService.GeoPoint;
import com.exploreceylon.backend.service.ItineraryAssemblyService.TripDay;
import com.exploreceylon.backend.service.budget.VisitDurationEstimator;
import com.exploreceylon.backend.util.HaversineDistanceCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DestinationSelectionEngineTest {

    private DefaultDestinationSelectionEngine selectionEngine;

    @BeforeEach
    void setUp() {
        selectionEngine = new DefaultDestinationSelectionEngine(
                new HaversineDistanceCalculator(),
                new VisitDurationEstimator()
        );
    }

    @Test
    @DisplayName("Should select and schedule destinations for Colombo -> Nuwara Eliya (3 Days)")
    void testColomboToNuwaraEliya_3Days() {
        GeoPoint colombo = new GeoPoint(6.9271, 79.8612);
        GeoPoint nuwaraEliya = new GeoPoint(6.9497, 80.7891);

        Destination d1 = Destination.builder().id(1L).name("Pinnawala Elephant Orphanage").district("Kegalle").category(DestinationCategory.WILDLIFE_NATURE).rating(4.5).latitude(7.3013).longitude(80.3860).build();
        Destination d2 = Destination.builder().id(2L).name("Kandy Temple of Tooth").district("Kandy").category(DestinationCategory.RELIGIOUS).rating(4.8).latitude(7.2906).longitude(80.6337).build();
        Destination d3 = Destination.builder().id(3L).name("Royal Botanical Gardens Peradeniya").district("Kandy").category(DestinationCategory.CULTURE_HERITAGE).rating(4.7).latitude(7.2683).longitude(80.5966).build();
        Destination d4 = Destination.builder().id(4L).name("Gregory Lake").district("Nuwara Eliya").category(DestinationCategory.HILL_COUNTRY).rating(4.4).latitude(6.9538).longitude(80.7811).build();
        Destination d5 = Destination.builder().id(5L).name("Pedro Tea Estate").district("Nuwara Eliya").category(DestinationCategory.CULTURE_HERITAGE).rating(4.6).latitude(6.9691).longitude(80.7964).build();

        List<DayBudget> dayBudgets = List.of(
                DayBudget.builder().dayNumber(1).availableSightseeingMinutes(390).maximumVisitCount(4).build(),
                DayBudget.builder().dayNumber(2).availableSightseeingMinutes(390).maximumVisitCount(4).build(),
                DayBudget.builder().dayNumber(3).availableSightseeingMinutes(390).maximumVisitCount(4).build()
        );

        SelectionContext context = SelectionContext.builder()
                .orderedCandidates(List.of(d1, d2, d3, d4, d5))
                .dayBudgets(dayBudgets)
                .origin(colombo)
                .destination(nuwaraEliya)
                .travelStyle("BALANCED")
                .tripDurationDays(3)
                .build();

        List<TripDay> scheduledDays = selectionEngine.selectAndScheduleDestinations(context);

        assertEquals(3, scheduledDays.size(), "Should produce 3 trip days");
        assertTrue(scheduledDays.get(0).destinations().size() > 0, "Day 1 should have allocated candidates");

        SelectionStatistics stats = selectionEngine.evaluateDaySelection(scheduledDays.get(0));
        assertNotNull(stats);
        assertTrue(stats.getDailyQualityScore() > 0.0, "Quality score should be positive");
    }

    @Test
    @DisplayName("Should prevent category repetition and respect category limits per day")
    void testCategoryBalancing() {
        GeoPoint colombo = new GeoPoint(6.9271, 79.8612);
        GeoPoint kandy = new GeoPoint(7.2906, 80.6337);

        Destination t1 = Destination.builder().id(10L).name("Temple 1").category(DestinationCategory.RELIGIOUS).latitude(7.0).longitude(80.0).rating(4.5).build();
        Destination t2 = Destination.builder().id(11L).name("Temple 2").category(DestinationCategory.RELIGIOUS).latitude(7.1).longitude(80.1).rating(4.5).build();
        Destination t3 = Destination.builder().id(12L).name("Temple 3").category(DestinationCategory.RELIGIOUS).latitude(7.2).longitude(80.2).rating(4.5).build();
        Destination m1 = Destination.builder().id(13L).name("Museum 1").category(DestinationCategory.CULTURE_HERITAGE).latitude(7.25).longitude(80.25).rating(4.5).build();

        List<DayBudget> dayBudgets = List.of(
                DayBudget.builder().dayNumber(1).availableSightseeingMinutes(390).maximumVisitCount(6).build()
        );

        SelectionContext context = SelectionContext.builder()
                .orderedCandidates(List.of(t1, t2, t3, m1))
                .dayBudgets(dayBudgets)
                .origin(colombo)
                .destination(kandy)
                .travelStyle("BALANCED")
                .tripDurationDays(1)
                .build();

        List<TripDay> scheduledDays = selectionEngine.selectAndScheduleDestinations(context);

        assertEquals(1, scheduledDays.size());
        List<Destination> day1Dests = scheduledDays.get(0).destinations();

        long religiousCount = day1Dests.stream().filter(d -> d.getCategory() == DestinationCategory.RELIGIOUS).count();
        assertTrue(religiousCount <= 2, "Religious temple count per day must not exceed 2");
    }
}
