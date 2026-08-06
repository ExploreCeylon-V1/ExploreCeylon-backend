package com.exploreceylon.backend.service.regeneration;

import com.exploreceylon.backend.dto.regeneration.RegenerationContext;
import com.exploreceylon.backend.dto.regeneration.TripRegenerationResult;

/**
 * Strategy interface for localized, intelligent trip regeneration.
 */
public interface TripRegenerationEngine {

    /**
     * Regenerates only affected days and pipeline modules after user edits while keeping locked and unaffected days 100% identical.
     *
     * @param context RegenerationContext containing original itinerary days, routeMatrix, and regeneration request.
     * @return TripRegenerationResult DTO.
     */
    TripRegenerationResult regenerateTrip(RegenerationContext context);
}
