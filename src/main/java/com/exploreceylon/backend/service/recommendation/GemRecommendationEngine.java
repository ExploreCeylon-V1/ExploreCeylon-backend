package com.exploreceylon.backend.service.recommendation;

import com.exploreceylon.backend.dto.recommendation.GemRecommendationContext;
import com.exploreceylon.backend.dto.recommendation.RecommendationStatistics;
import com.exploreceylon.backend.dto.recommendation.RecommendedGem;

import java.util.List;

/**
 * Strategy interface for recommending Hidden Gems and Seasonal Events to fill remaining itinerary time/distance gaps.
 */
public interface GemRecommendationEngine {

    /**
     * Identifies eligible hidden gems and events, scores them according to rating/corridor/diversity,
     * and returns recommended stops for the target trip day.
     *
     * @param context GemRecommendationContext containing trip day, scheduled stops, pre-computed RouteMatrix, and candidate pools.
     * @return List of RecommendedGem DTOs.
     */
    List<RecommendedGem> recommendGemsAndEvents(GemRecommendationContext context);

    /**
     * Calculates recommendation statistics and quality improvement metrics for the daily itinerary.
     *
     * @param context GemRecommendationContext containing trip day and candidate pools.
     * @param recommendations Previously computed recommendations.
     * @return RecommendationStatistics DTO.
     */
    RecommendationStatistics computeStatistics(GemRecommendationContext context, List<RecommendedGem> recommendations);
}
