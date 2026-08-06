package com.exploreceylon.backend.service.planner;

import com.exploreceylon.backend.dto.planner.PlannerRequest;
import com.exploreceylon.backend.dto.planner.PlannerResponse;

/**
 * High-level Facade service orchestrating the complete 11-phase itinerary optimization pipeline.
 */
public interface PlannerFacadeService {

    /**
     * Executes the complete trip planning pipeline and returns a production-ready PlannerResponse DTO.
     *
     * @param request PlannerRequest containing user parameters.
     * @return PlannerResponse DTO.
     */
    PlannerResponse generateItinerary(PlannerRequest request);
}
