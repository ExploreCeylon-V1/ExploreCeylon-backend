package com.exploreceylon.backend.service.planner;

import com.exploreceylon.backend.dto.planner.*;
import com.exploreceylon.backend.model.*;
import com.exploreceylon.backend.repository.PlannerCostSnapshotRepository;
import com.exploreceylon.backend.repository.PlannerMetadataRepository;
import com.exploreceylon.backend.repository.TripRepository;
import com.exploreceylon.backend.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PlannerPersistenceIntegrationTest {

    @Autowired
    private PlannerPersistenceService persistenceService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private PlannerMetadataRepository metadataRepository;

    @Autowired
    private PlannerCostSnapshotRepository costSnapshotRepository;

    @Test
    @DisplayName("End-to-End integration test: Generate & save trip, metadata, read-only cost snapshot, confirm, duplicate & soft-delete")
    void testEndToEndPlannerPersistenceLifecycle() {
        User user = userRepository.save(User.builder()
                .name("E2E User")
                .email("e2e_persist@example.com")
                .password("password123")
                .role(User.Role.TRAVELER)
                .build());

        PlannerRequest request = PlannerRequest.builder()
                .origin("Colombo")
                .destination("Kandy")
                .tripDays(2)
                .budget("MID_RANGE")
                .travelStyle("RELAXED")
                .groupSize(2)
                .startDate(LocalDate.now())
                .build();

        PlannerSaveRequest saveRequest = PlannerSaveRequest.builder()
                .plannerRequest(request)
                .customTripTitle("E2E Persisted Journey")
                .autoConfirm(false)
                .build();

        // 1. Generate & Save
        PlannerSaveResponse saveResponse = persistenceService.generateAndSave(saveRequest, user);
        assertThat(saveResponse).isNotNull();
        Long tripId = saveResponse.getTripId();
        assertThat(tripId).isNotNull();

        // 2. Verify Trip & Days Persisted
        Optional<Trip> savedTripOpt = tripRepository.findById(tripId);
        assertThat(savedTripOpt).isPresent();
        Trip savedTrip = savedTripOpt.get();
        assertThat(savedTrip.getTitle()).isEqualTo("E2E Persisted Journey");
        assertThat(savedTrip.getDays()).isNotEmpty();

        // 3. Verify Metadata & Read-Only Cost Snapshot Persisted
        Optional<PlannerMetadata> metaOpt = metadataRepository.findByTripId(tripId);
        assertThat(metaOpt).isPresent();
        assertThat(metaOpt.get().getPlannerVersion()).isEqualTo("13.0");

        Optional<PlannerCostSnapshot> costOpt = costSnapshotRepository.findByTripId(tripId);
        assertThat(costOpt).isPresent();
        assertThat(costOpt.get().getGrandTotal()).isGreaterThan(0.0);

        // 4. Confirm Trip
        PlannerTripSummary confirmedSummary = persistenceService.confirmTrip(tripId, user);
        assertThat(confirmedSummary.getStatus()).isEqualTo(Trip.TripStatus.CONFIRMED);

        // 5. Duplicate Trip
        PlannerTripSummary dupSummary = persistenceService.duplicateTrip(tripId, user);
        assertThat(dupSummary.getTripId()).isNotEqualTo(tripId);
        assertThat(dupSummary.getTitle()).startsWith("Copy of");

        // 6. Soft Delete Original Trip
        persistenceService.softDeleteTrip(tripId, user);
        Trip deletedTrip = tripRepository.findById(tripId).orElseThrow();
        assertThat(deletedTrip.getStatus()).isEqualTo(Trip.TripStatus.CANCELLED);
    }
}
