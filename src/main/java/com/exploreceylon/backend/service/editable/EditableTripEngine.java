package com.exploreceylon.backend.service.editable;

import com.exploreceylon.backend.dto.editable.EditContext;
import com.exploreceylon.backend.dto.editable.TripEditResult;

/**
 * Strategy interface for localized, smart itinerary editing and re-optimization.
 */
public interface EditableTripEngine {

    /**
     * Executes localized edits on an existing itinerary without regenerating unaffected days or re-running full planner.
     *
     * @param context EditContext containing original itinerary days, pre-computed RouteMatrix, and edit operations.
     * @return TripEditResult containing updated days and execution statistics.
     */
    TripEditResult applyEdits(EditContext context);
}
