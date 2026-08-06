package com.exploreceylon.backend.dto.budget;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO capturing realistic time and travel budgets for a single trip day.
 * Used internally by planner scheduling and stop allocation engines.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DayBudget {
    private int dayNumber;
    private int availableDrivingMinutes;
    private int availableSightseeingMinutes;
    private int reservedMealMinutes;
    private int reservedBufferMinutes;
    private double drivingDistanceTargetKm;
    private int remainingMinutes;
    private int maximumVisitCount;
}
