package com.exploreceylon.backend.dto.selection;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO holding evaluation metrics and quality scores for selected day itineraries.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SelectionStatistics {
    private double dailyQualityScore;       // 0 - 100 scale
    private int categoryDiversityCount;
    private double averageRating;
    private double totalDrivingDistanceKm;
    private int unusedSightseeingMinutes;
    private int totalStopsCount;
}
