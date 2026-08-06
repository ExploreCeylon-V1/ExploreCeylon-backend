package com.exploreceylon.backend.service.planner;

import com.exploreceylon.backend.dto.planner.PlannerResponse;
import com.exploreceylon.backend.dto.planner.PlannerSaveRequest;
import com.exploreceylon.backend.dto.planner.PlannerSaveResponse;
import com.exploreceylon.backend.dto.planner.PlannerTripSummary;
import com.exploreceylon.backend.model.User;

import java.util.List;

/**
 * Interface contract for persisting, retrieving, and soft-deleting generated user trips.
 */
public interface PlannerPersistenceService {

    PlannerSaveResponse generateAndSave(PlannerSaveRequest saveRequest, User authenticatedUser);

    List<PlannerTripSummary> getUserGeneratedTrips(User authenticatedUser);

    PlannerResponse getGeneratedTripById(Long tripId, User authenticatedUser);

    void softDeleteTrip(Long tripId, User authenticatedUser);
}
