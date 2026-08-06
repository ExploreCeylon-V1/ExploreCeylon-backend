package com.exploreceylon.backend.service.planner;

import com.exploreceylon.backend.dto.cost.CostBreakdown;
import com.exploreceylon.backend.dto.cost.TripCostEstimate;
import com.exploreceylon.backend.dto.planner.*;
import com.exploreceylon.backend.model.Trip;
import com.exploreceylon.backend.model.Trip.TripStatus;
import com.exploreceylon.backend.model.User;
import com.exploreceylon.backend.repository.TripRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class PlannerPersistenceServiceTest {

    private DefaultPlannerPersistenceService persistenceService;
    private PlannerFacadeService facadeService;
    private TripRepository tripRepository;
    private PlannerTripMapper tripMapper;

    private User owner;
    private User attacker;

    @BeforeEach
    void setUp() {
        facadeService = Mockito.mock(PlannerFacadeService.class);
        tripRepository = Mockito.mock(TripRepository.class);
        tripMapper = new PlannerTripMapper();

        persistenceService = new DefaultPlannerPersistenceService(facadeService, tripRepository, tripMapper);

        owner = User.builder().id(1L).email("owner@example.com").role(User.Role.TRAVELER).build();
        attacker = User.builder().id(2L).email("attacker@example.com").role(User.Role.TRAVELER).build();
    }

    @Test
    @DisplayName("Should generate and save persistent trip, initializing budgetAmountLkr")
    void testGenerateAndSaveSuccess() {
        PlannerRequest request = PlannerRequest.builder()
                .origin("Colombo")
                .destination("Kandy")
                .tripDays(2)
                .budget("MID_RANGE")
                .travelStyle("RELAXED")
                .groupSize(2)
                .startDate(LocalDate.of(2026, 9, 1))
                .build();

        PlannerResponse response = PlannerResponse.builder()
                .summary(PlannerSummary.builder().origin("Colombo").destination("Kandy").tripDays(2).build())
                .days(List.of())
                .estimatedCost(TripCostEstimate.builder().grandTotal(32175.0).totalBreakdown(CostBreakdown.builder().total(32175.0).build()).build())
                .build();

        when(facadeService.generateItinerary(any())).thenReturn(response);

        Trip savedEntity = Trip.builder()
                .id(100L)
                .user(owner)
                .title("Colombo to Kandy Trip")
                .fromLocation("Colombo")
                .toLocation("Kandy")
                .startDate(LocalDate.of(2026, 9, 1))
                .endDate(LocalDate.of(2026, 9, 2))
                .budgetAmountLkr(32175.0)
                .status(TripStatus.GENERATED)
                .shareToken("share-123")
                .build();

        when(tripRepository.save(any())).thenReturn(savedEntity);

        PlannerSaveRequest saveRequest = PlannerSaveRequest.builder()
                .plannerRequest(request)
                .build();

        PlannerSaveResponse saveResponse = persistenceService.generateAndSave(saveRequest, owner);

        assertNotNull(saveResponse);
        assertEquals(100L, saveResponse.getTripId());
        assertEquals(TripStatus.GENERATED, saveResponse.getStatus());
        assertNotNull(saveResponse.getPlannerResponse());
    }

    @Test
    @DisplayName("Should block non-owner access to trip (anti-IDOR protection)")
    void testAntiIdorProtection() {
        Trip trip = Trip.builder()
                .id(200L)
                .user(owner) // Owned by user 1
                .fromLocation("Colombo")
                .toLocation("Kandy")
                .startDate(LocalDate.of(2026, 9, 1))
                .endDate(LocalDate.of(2026, 9, 2))
                .build();

        when(tripRepository.findById(200L)).thenReturn(Optional.of(trip));

        assertThrows(SecurityException.class, () -> {
            persistenceService.getGeneratedTripById(200L, attacker); // Attacker user 2 tries access
        }, "SecurityException must be thrown when non-owner accesses trip");
    }
}
