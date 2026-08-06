package com.exploreceylon.backend.dto.cost;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Statistics DTO capturing high-level cost metrics.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CostStatistics {
    private double averageCostPerDay;
    private int highestCostDayNumber;
    private double costPerKmRatio;
}
