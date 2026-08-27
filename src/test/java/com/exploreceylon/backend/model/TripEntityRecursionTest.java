package com.exploreceylon.backend.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TripEntityRecursionTest {

    @Test
    @DisplayName("Verify bidirectional entity relationships do not cause StackOverflowError on hashCode, equals, or toString")
    void testNoStackOverflowOnBidirectionalRelationships() {
        Trip trip = Trip.builder()
                .id(23L)
                .title("5 Days - Southern Coastal Exploration")
                .fromLocation("Colombo")
                .toLocation("Galle")
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(5))
                .days(new ArrayList<>())
                .activityLogs(new ArrayList<>())
                .build();

        PlannerMetadata metadata = PlannerMetadata.builder()
                .id(101L)
                .trip(trip)
                .plannerVersion("13.0")
                .build();
        trip.setPlannerMetadata(metadata);

        PlannerCostSnapshot snapshot = PlannerCostSnapshot.builder()
                .id(201L)
                .trip(trip)
                .grandTotal(500.0)
                .build();
        trip.setPlannerCostSnapshot(snapshot);

        TripPreference preference = TripPreference.builder()
                .id(301L)
                .trip(trip)
                .toLocation("Galle")
                .build();
        trip.setPreference(preference);

        TripDay day = TripDay.builder()
                .id(401L)
                .trip(trip)
                .dayNumber(1)
                .date(LocalDate.now())
                .items(new ArrayList<>())
                .build();
        trip.getDays().add(day);

        TripDayItem item = TripDayItem.builder()
                .id(501L)
                .tripDay(day)
                .title("Galle Fort Walk")
                .type(TripDayItem.ItemType.ACTIVITY)
                .build();
        day.getItems().add(item);

        TripActivityLog log = TripActivityLog.builder()
                .id(601L)
                .trip(trip)
                .actionType("TRIP_GENERATED")
                .description("Itinerary created")
                .build();
        trip.getActivityLogs().add(log);

        Budget budget = Budget.builder()
                .id(701L)
                .trip(trip)
                .totalBudget(1000.0)
                .items(new ArrayList<>())
                .build();

        BudgetItem budgetItem = BudgetItem.builder()
                .id(801L)
                .budget(budget)
                .title("Hotel in Galle")
                .category(BudgetItem.ItemCategory.HOTEL)
                .amount(200.0)
                .build();
        budget.getItems().add(budgetItem);

        // 1. Verify hashCode() executes without StackOverflowError
        assertDoesNotThrow(() -> {
            int h1 = trip.hashCode();
            int h2 = metadata.hashCode();
            int h3 = snapshot.hashCode();
            int h4 = preference.hashCode();
            int h5 = day.hashCode();
            int h6 = item.hashCode();
            int h7 = log.hashCode();
            int h8 = budget.hashCode();
            int h9 = budgetItem.hashCode();
            assertTrue(h1 != 0 && h2 != 0 && h3 != 0);
        });

        // 2. Verify equals() executes without StackOverflowError
        assertDoesNotThrow(() -> {
            Trip anotherTrip = Trip.builder().id(23L).build();
            assertEquals(trip, anotherTrip);

            PlannerMetadata anotherMeta = PlannerMetadata.builder().id(101L).build();
            assertEquals(metadata, anotherMeta);

            TripDay anotherDay = TripDay.builder().id(401L).build();
            assertEquals(day, anotherDay);
        });

        // 3. Verify toString() executes without StackOverflowError
        assertDoesNotThrow(() -> {
            String s1 = trip.toString();
            String s2 = metadata.toString();
            String s3 = snapshot.toString();
            String s4 = preference.toString();
            String s5 = day.toString();
            String s6 = item.toString();
            String s7 = log.toString();
            String s8 = budget.toString();
            String s9 = budgetItem.toString();

            assertNotNull(s1);
            assertNotNull(s2);
            assertNotNull(s3);
            assertNotNull(s8);
        });
    }
}
