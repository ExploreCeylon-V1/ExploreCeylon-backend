package com.exploreceylon.backend.service.cost;

import com.exploreceylon.backend.dto.cost.TripCostEstimate;
import com.exploreceylon.backend.dto.matrix.RouteMatrix;
import com.exploreceylon.backend.service.ItineraryAssemblyService.PlannedDay;

import java.util.List;

/**
 * Strategy interface for pure, deterministic trip cost estimation without external API calls.
 */
public interface TripCostEngine {

    /**
     * Calculates cost estimates and detailed breakdowns for the given itinerary using RouteMatrix.
     *
     * @param plannedDays List of planned trip days containing stops.
     * @param routeMatrix Pre-computed RouteMatrix for distance calculation.
     * @param travelStyle Travel style (RELAXED, BALANCED, FAST_PACED).
     * @param groupSize Number of travelers.
     * @return TripCostEstimate DTO.
     */
    TripCostEstimate estimateTripCost(List<PlannedDay> plannedDays, RouteMatrix routeMatrix, String travelStyle, int groupSize);
}
