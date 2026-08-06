package com.exploreceylon.backend.dto.cost;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Top-level DTO representing the complete trip cost estimate.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripCostEstimate {
    private String tripTitle;
    private String currency;              // Default "LKR"
    private List<DayCostEstimate> dailyEstimates;
    private CostBreakdown totalBreakdown;
    private double grandTotal;
    private CostStatistics statistics;
}
