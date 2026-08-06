package com.exploreceylon.backend.service.ranking;

import com.exploreceylon.backend.model.BudgetLevel;
import com.exploreceylon.backend.service.ItineraryAssemblyService.GeoPoint;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Set;

/**
 * Contextual parameters passed to the DestinationRankingEngine to evaluate
 * candidate destinations for a specific trip itinerary.
 */
@Data
@Builder
public class RankingContext {
    private GeoPoint origin;
    private GeoPoint currentPosition;
    private GeoPoint destination;
    private List<String> travelStyles;
    private Set<String> tripMonths;
    private BudgetLevel budgetLevel;
}
