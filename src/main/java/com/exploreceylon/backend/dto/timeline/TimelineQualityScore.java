package com.exploreceylon.backend.dto.timeline;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO capturing fine-grained quality score sub-components on a 0-100 scale.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimelineQualityScore {
    private double totalScore;              // 0 - 100
    private double mealTimingScore;         // 0 - 20
    private double waitingPenaltyScore;     // 0 - 20
    private double drivingEfficiencyScore;  // 0 - 20
    private double preferredVisitScore;     // 0 - 20
    private double openingHoursScore;       // 0 - 20
}
