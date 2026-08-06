package com.exploreceylon.backend.service.planner;

import com.exploreceylon.backend.dto.planner.*;
import com.exploreceylon.backend.model.*;
import com.exploreceylon.backend.model.Trip.TripStatus;
import com.exploreceylon.backend.repository.PlannerCostSnapshotRepository;
import com.exploreceylon.backend.repository.PlannerMetadataRepository;
import com.exploreceylon.backend.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Default implementation of PlannerPersistenceService for Phase 13.
 * Handles single-transaction persistence of generated trips, metadata, read-only cost snapshots,
 * ownership verification (anti-IDOR), trip confirmation, duplication, and soft-deletion.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultPlannerPersistenceService implements PlannerPersistenceService {

    private final PlannerFacadeService plannerFacadeService;
    private final TripRepository tripRepository;
    private final PlannerMetadataRepository plannerMetadataRepository;
    private final PlannerCostSnapshotRepository plannerCostSnapshotRepository;
    private final PlannerTripMapper plannerTripMapper;

    @Override
    @Transactional
    public PlannerSaveResponse generateAndSave(PlannerSaveRequest saveRequest, User authenticatedUser) {
        if (saveRequest == null || saveRequest.getPlannerRequest() == null || authenticatedUser == null) {
            throw new IllegalArgumentException("Save request and authenticated user are required.");
        }

        PlannerRequest request = saveRequest.getPlannerRequest();
        PlannerResponse response = plannerFacadeService.generateItinerary(request);

        Trip tripEntity = plannerTripMapper.mapToEntity(request, response, authenticatedUser);
        if (saveRequest.getCustomTripTitle() != null && !saveRequest.getCustomTripTitle().isBlank()) {
            tripEntity.setTitle(saveRequest.getCustomTripTitle());
        }
        if (Boolean.TRUE.equals(saveRequest.getAutoConfirm())) {
            tripEntity.setStatus(TripStatus.CONFIRMED);
        }

        Trip savedTrip = tripRepository.save(tripEntity);

        // Save Planner Metadata
        PlannerMetadata metadata = plannerTripMapper.mapToMetadata(response, savedTrip);
        if (metadata != null) {
            plannerMetadataRepository.save(metadata);
        }

        // Save Read-Only AI Estimated Cost Snapshot
        PlannerCostSnapshot costSnapshot = plannerTripMapper.mapToCostSnapshot(response, savedTrip);
        if (costSnapshot != null) {
            plannerCostSnapshotRepository.save(costSnapshot);
        }

        response.setTripId(savedTrip.getId());
        response.setCreatedAt(savedTrip.getCreatedAt());
        response.setOwner(authenticatedUser.getEmail());
        response.setStatus(savedTrip.getStatus());

        log.info("Successfully generated and saved persistent Trip ID {} for user {}", savedTrip.getId(), authenticatedUser.getEmail());

        return PlannerSaveResponse.builder()
                .tripId(savedTrip.getId())
                .shareToken(savedTrip.getShareToken())
                .status(savedTrip.getStatus())
                .createdAt(savedTrip.getCreatedAt())
                .plannerResponse(response)
                .build();
    }

    @Override
    @Transactional
    public PlannerTripSummary confirmTrip(Long tripId, User authenticatedUser) {
        Trip trip = findTripAndVerifyOwner(tripId, authenticatedUser);
        trip.setStatus(TripStatus.CONFIRMED);
        Trip updatedTrip = tripRepository.save(trip);
        log.info("Trip ID {} confirmed by user {}", tripId, authenticatedUser.getEmail());
        return plannerTripMapper.mapToSummary(updatedTrip);
    }

    @Override
    @Transactional
    public PlannerTripSummary duplicateTrip(Long tripId, User authenticatedUser) {
        Trip originalTrip = findTripAndVerifyOwner(tripId, authenticatedUser);

        Trip duplicatedTrip = Trip.builder()
                .user(authenticatedUser)
                .title("Copy of " + originalTrip.getTitle())
                .fromLocation(originalTrip.getFromLocation())
                .toLocation(originalTrip.getToLocation())
                .startDate(originalTrip.getStartDate())
                .endDate(originalTrip.getEndDate())
                .travelStyle(originalTrip.getTravelStyle())
                .budgetRange(originalTrip.getBudgetRange())
                .groupSize(originalTrip.getGroupSize())
                .budgetAmountLkr(originalTrip.getBudgetAmountLkr())
                .status(TripStatus.GENERATED)
                .aiGenerated(true)
                .shareToken(UUID.randomUUID().toString())
                .build();

        Trip savedDuplicate = tripRepository.save(duplicatedTrip);
        log.info("Trip ID {} duplicated into new Trip ID {} for user {}", tripId, savedDuplicate.getId(), authenticatedUser.getEmail());
        return plannerTripMapper.mapToSummary(savedDuplicate);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlannerTripSummary> getUserGeneratedTrips(User authenticatedUser) {
        if (authenticatedUser == null) return List.of();
        List<Trip> trips = tripRepository.findByUserIdOrderByCreatedAtDesc(authenticatedUser.getId());
        return trips.stream().map(plannerTripMapper::mapToSummary).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public PlannerResponse getGeneratedTripById(Long tripId, User authenticatedUser) {
        Trip trip = findTripAndVerifyOwner(tripId, authenticatedUser);

        PlannerRequest request = PlannerRequest.builder()
                .origin(trip.getFromLocation())
                .destination(trip.getToLocation())
                .tripDays((int) (trip.getEndDate().toEpochDay() - trip.getStartDate().toEpochDay() + 1))
                .budget(trip.getBudgetRange() != null ? trip.getBudgetRange().name() : "MID_RANGE")
                .travelStyle(trip.getTravelStyle() != null ? trip.getTravelStyle().name() : "RELAXED")
                .groupSize(trip.getGroupSize() != null ? trip.getGroupSize() : 1)
                .startDate(trip.getStartDate())
                .build();

        PlannerResponse response = plannerFacadeService.generateItinerary(request);
        response.setTripId(trip.getId());
        response.setCreatedAt(trip.getCreatedAt());
        response.setOwner(trip.getUser().getEmail());
        response.setStatus(trip.getStatus());

        return response;
    }

    @Override
    @Transactional
    public void softDeleteTrip(Long tripId, User authenticatedUser) {
        Trip trip = findTripAndVerifyOwner(tripId, authenticatedUser);
        trip.setStatus(TripStatus.CANCELLED);
        tripRepository.save(trip);
        log.info("Soft-deleted Trip ID {} by user {}", tripId, authenticatedUser.getEmail());
    }

    private Trip findTripAndVerifyOwner(Long tripId, User user) {
        if (tripId == null || user == null) {
            throw new IllegalArgumentException("Trip ID and User are required.");
        }
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new IllegalArgumentException("Trip not found with ID: " + tripId));

        boolean isOwner = Objects.equals(trip.getUser().getId(), user.getId());
        boolean isAdmin = user.getRole() == User.Role.ADMIN;

        if (!isOwner && !isAdmin) {
            log.warn("IDOR attempt blocked: User {} tried accessing Trip ID {} owned by User {}",
                    user.getEmail(), tripId, trip.getUser().getId());
            throw new SecurityException("Access denied: You are not authorized to access this trip.");
        }

        return trip;
    }
}
