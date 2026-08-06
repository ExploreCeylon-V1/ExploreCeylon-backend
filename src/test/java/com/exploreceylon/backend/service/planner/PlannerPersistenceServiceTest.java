package com.exploreceylon.backend.service.planner;

import com.exploreceylon.backend.dto.cost.CostBreakdown;
import com.exploreceylon.backend.dto.cost.TripCostEstimate;
import com.exploreceylon.backend.dto.planner.*;
import com.exploreceylon.backend.model.Trip;
import com.exploreceylon.backend.model.Trip.TripStatus;
import com.exploreceylon.backend.model.User;
import com.exploreceylon.backend.repository.PlannerCostSnapshotRepository;
import com.exploreceylon.backend.repository.PlannerMetadataRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlannerPersistenceServiceTest {

    private DefaultPlannerPersistenceService persistenceService;
    private PlannerFacadeService facadeService;
    private TripRepository tripRepository;
    private PlannerMetadataRepository metadataRepository;
    private PlannerCostSnapshotRepository costSnapshotRepository;
    private PlannerTripMapper tripMapper;
    private com.exploreceylon.backend.repository.TripActivityLogRepository activityLogRepository;

    private User owner;
    private User attacker;

    @BeforeEach
    void setUp() {
        facadeService = Mockito.mock(PlannerFacadeService.class);
        tripRepository = Mockito.mock(TripRepository.class);
        metadataRepository = Mockito.mock(PlannerMetadataRepository.class);
        costSnapshotRepository = Mockito.mock(PlannerCostSnapshotRepository.class);
        activityLogRepository = Mockito.mock(com.exploreceylon.backend.repository.TripActivityLogRepository.class);
        tripMapper = new PlannerTripMapper();

        persistenceService = new DefaultPlannerPersistenceService(
                facadeService, tripRepository, metadataRepository, costSnapshotRepository, tripMapper, activityLogRepository);

        owner = User.builder().id(1L).email("owner@example.com").role(User.Role.TRAVELER).build();
        attacker = User.builder().id(2L).email("attacker@example.com").role(User.Role.TRAVELER).build();
    }

    @Test
    @DisplayName("Should generate and save persistent trip, metadata, and read-only cost snapshot")
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
                .qualityScore(95.0)
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

        verify(metadataRepository).save(any());
        verify(costSnapshotRepository).save(any());
    }

    @Test
    @DisplayName("Should confirm trip status to CONFIRMED")
    void testConfirmTripSuccess() {
        Trip trip = Trip.builder()
                .id(100L)
                .user(owner)
                .status(TripStatus.GENERATED)
                .build();

        when(tripRepository.findById(100L)).thenReturn(Optional.of(trip));
        when(tripRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PlannerTripSummary summary = persistenceService.confirmTrip(100L, owner);

        assertNotNull(summary);
        assertEquals(TripStatus.CONFIRMED, summary.getStatus());
    }

    @Test
    @DisplayName("Should duplicate existing trip")
    void testDuplicateTripSuccess() {
        Trip trip = Trip.builder()
                .id(100L)
                .user(owner)
                .title("Original Trip")
                .fromLocation("Colombo")
                .toLocation("Kandy")
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(2))
                .status(TripStatus.GENERATED)
                .build();

        Trip duplicateSaved = Trip.builder()
                .id(101L)
                .user(owner)
                .title("Copy of Original Trip")
                .fromLocation("Colombo")
                .toLocation("Kandy")
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(2))
                .status(TripStatus.GENERATED)
                .shareToken("new-share-token")
                .build();

        when(tripRepository.findById(100L)).thenReturn(Optional.of(trip));
        when(tripRepository.save(any())).thenReturn(duplicateSaved);

        PlannerTripSummary summary = persistenceService.duplicateTrip(100L, owner);

        assertNotNull(summary);
        assertEquals(101L, summary.getTripId());
        assertTrue(summary.getTitle().contains("Copy of"));
    }

    @Test
    @DisplayName("Should block non-owner access to trip (anti-IDOR protection)")
    void testAntiIdorProtection() {
        Trip trip = Trip.builder()
                .id(200L)
                .user(owner)
                .fromLocation("Colombo")
                .toLocation("Kandy")
                .startDate(LocalDate.of(2026, 9, 1))
                .endDate(LocalDate.of(2026, 9, 2))
                .build();

        when(tripRepository.findById(200L)).thenReturn(Optional.of(trip));

        assertThrows(SecurityException.class, () -> {
            persistenceService.getGeneratedTripById(200L, attacker);
        }, "SecurityException must be thrown when non-owner accesses trip");
    }
}
