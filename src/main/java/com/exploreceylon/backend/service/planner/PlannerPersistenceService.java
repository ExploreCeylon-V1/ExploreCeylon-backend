package com.exploreceylon.backend.service.planner;

import com.exploreceylon.backend.dto.planner.PlannerResponse;
import com.exploreceylon.backend.dto.planner.PlannerSaveRequest;
import com.exploreceylon.backend.dto.planner.PlannerSaveResponse;
import com.exploreceylon.backend.dto.planner.PlannerTripSummary;
import com.exploreceylon.backend.model.User;

import java.util.List;

/**
 * Interface contract for persisting, retrieving, confirming, duplicating, and soft-deleting generated user trips.
 */
public interface PlannerPersistenceService {

    PlannerSaveResponse generateAndSave(PlannerSaveRequest saveRequest, User authenticatedUser);

    PlannerTripSummary confirmTrip(Long tripId, User authenticatedUser);

    PlannerTripSummary duplicateTrip(Long tripId, User authenticatedUser);

    List<PlannerTripSummary> getUserGeneratedTrips(User authenticatedUser);

    PlannerResponse getGeneratedTripById(Long tripId, User authenticatedUser);

    void softDeleteTrip(Long tripId, User authenticatedUser);

    PlannerTripSummary restoreTrip(Long tripId, User authenticatedUser);

    PlannerTripSummary revokeShareToken(Long tripId, User authenticatedUser);

    PlannerTripSummary regenerateShareToken(Long tripId, User authenticatedUser);

    List<com.exploreceylon.backend.model.TripActivityLog> getTripActivityLogs(Long tripId, User authenticatedUser);

    void recordActivityLog(Long tripId, String actionType, String description, String performedBy);
}
