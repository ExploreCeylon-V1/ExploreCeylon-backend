package com.exploreceylon.backend.service.progression;

import com.exploreceylon.backend.dto.progression.JourneyProgress;
import com.exploreceylon.backend.model.Destination;

import java.util.List;

/**
 * Strategy interface for calculating journey progress along a road route
 * and ordering candidate destinations sequentially to enforce forward progression.
 */
public interface JourneyProgressionEngine {

    /**
     * Orders candidate destinations in ascending order of cumulative travelled distance along the road route.
     *
     * @param candidates List of candidate destinations inside the travel corridor.
     * @param context    Progression context parameters (route path, minimum forward distance).
     * @return List of destinations ordered strictly by road progression distance.
     */
    List<Destination> orderCandidatesByProgress(List<Destination> candidates, ProgressionContext context);

    /**
     * Calculates the detailed journey progress for a specific candidate destination along the route.
     *
     * @param destination Candidate destination entity.
     * @param context     Progression context parameters.
     * @return JourneyProgress DTO with progressDistanceKm, remainingDistanceKm, and segment index.
     */
    JourneyProgress calculateProgress(Destination destination, ProgressionContext context);
}
