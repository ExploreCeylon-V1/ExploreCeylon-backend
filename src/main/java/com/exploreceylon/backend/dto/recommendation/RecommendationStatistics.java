package com.exploreceylon.backend.dto.recommendation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Statistics DTO capturing recommendation metrics and gap utilization.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationStatistics {
    private int recommendedGemCount;
    private int recommendedEventCount;
    private int unusedMinutesBefore;
    private int unusedMinutesAfter;
    private double qualityImprovement;
    private double averageGemRating;
}
