package com.exploreceylon.backend.service.editable;

import com.exploreceylon.backend.dto.editable.*;
import com.exploreceylon.backend.dto.matrix.RouteMatrix;
import com.exploreceylon.backend.service.ItineraryAssemblyService.PlannedDay;
import com.exploreceylon.backend.service.ItineraryAssemblyService.PlannedStop;
import com.exploreceylon.backend.service.ItineraryAssemblyService.StopType;
import com.exploreceylon.backend.service.budget.VisitDurationEstimator;
import com.exploreceylon.backend.service.timeline.DefaultAttractionScheduleEngine;
import com.exploreceylon.backend.service.timeline.OpeningHoursService;
import com.exploreceylon.backend.service.timeline.PreferredVisitWindowService;
import com.exploreceylon.backend.service.timeline.TimelineValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EditableTripEngineTest {

    private DefaultEditableTripEngine editableTripEngine;

    @BeforeEach
    void setUp() {
        OpeningHoursService openingHoursService = new OpeningHoursService();
        PreferredVisitWindowService windowService = new PreferredVisitWindowService();
        VisitDurationEstimator durationEstimator = new VisitDurationEstimator();

        TimelineValidationService validationService = new TimelineValidationService(openingHoursService, windowService);
        DefaultAttractionScheduleEngine scheduleEngine = new DefaultAttractionScheduleEngine(openingHoursService, windowService, durationEstimator);

        editableTripEngine = new DefaultEditableTripEngine(validationService, scheduleEngine);
    }

    @Test
    @DisplayName("Should successfully remove a stop from Day 1 without affecting Day 2")
    void testRemoveStopFromDay1() {
        PlannedStop s1 = new PlannedStop(StopType.DESTINATION, 1L, "Pinnawala", "Kegalle", 7.3013, 80.3860, 60, 10.0, "MORNING");
        PlannedStop s2 = new PlannedStop(StopType.DESTINATION, 2L, "Kandy Temple", "Kandy", 7.2906, 80.6337, 60, 15.0, "AFTERNOON");

        PlannedDay day1 = new PlannedDay(1, LocalDate.of(2026, 9, 1), "Kegalle", new ArrayList<>(List.of(s1)), 50.0);
        PlannedDay day2 = new PlannedDay(2, LocalDate.of(2026, 9, 2), "Kandy", new ArrayList<>(List.of(s2)), 60.0);

        EditOperation op = EditOperation.builder()
                .type(EditOperationType.REMOVE_STOP)
                .dayNumber(1)
                .stopId(1L)
                .build();

        EditContext context = EditContext.builder()
                .originalDays(List.of(day1, day2))
                .operations(List.of(op))
                .lockedStopIds(Set.of())
                .build();

        TripEditResult result = editableTripEngine.applyEdits(context);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(1, result.getAffectedDayNumbers().size());
        assertEquals(1, result.getAffectedDayNumbers().get(0));
        assertTrue(result.getUpdatedDays().get(0).stops().isEmpty(), "Day 1 stop should be removed");
        assertEquals(1, result.getUpdatedDays().get(1).stops().size(), "Day 2 stops must remain untouched");
        assertEquals(100.0, result.getStatistics().getMatrixReusePercentage(), "Matrix reuse must be 100%");
    }

    @Test
    @DisplayName("Should reject edit when attempting to modify a locked stop")
    void testLockedStopProtection() {
        PlannedStop s1 = new PlannedStop(StopType.DESTINATION, 100L, "Sigiriya", "Matale", 7.9570, 80.7600, 120, 30.0, "MORNING");
        PlannedDay day1 = new PlannedDay(1, LocalDate.of(2026, 9, 1), "Matale", new ArrayList<>(List.of(s1)), 70.0);

        EditOperation op = EditOperation.builder()
                .type(EditOperationType.REMOVE_STOP)
                .dayNumber(1)
                .stopId(100L)
                .build();

        EditContext context = EditContext.builder()
                .originalDays(List.of(day1))
                .operations(List.of(op))
                .lockedStopIds(Set.of(100L)) // Locked!
                .build();

        TripEditResult result = editableTripEngine.applyEdits(context);

        assertNotNull(result);
        assertFalse(result.isSuccess(), "Edit must fail when stop is locked");
        assertTrue(result.getMessage().contains("locked"));
    }

    @Test
    @DisplayName("Should update visit duration for target stop")
    void testChangeVisitDuration() {
        PlannedStop s1 = new PlannedStop(StopType.DESTINATION, 50L, "Royal Botanical Gardens", "Kandy", 7.2683, 80.5966, 60, 10.0, "AFTERNOON");
        PlannedDay day1 = new PlannedDay(1, LocalDate.of(2026, 9, 1), "Kandy", new ArrayList<>(List.of(s1)), 40.0);

        EditOperation op = EditOperation.builder()
                .type(EditOperationType.CHANGE_VISIT_DURATION)
                .dayNumber(1)
                .stopId(50L)
                .newVisitDurationMinutes(120)
                .build();

        EditContext context = EditContext.builder()
                .originalDays(List.of(day1))
                .operations(List.of(op))
                .build();

        TripEditResult result = editableTripEngine.applyEdits(context);

        assertTrue(result.isSuccess());
        assertEquals(120, result.getUpdatedDays().get(0).stops().get(0).visitDurationMinutes());
    }
}
