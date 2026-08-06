package com.exploreceylon.backend.dto.planner;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Statistics DTO capturing end-to-end planning performance and route metrics.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlannerStatistics {
    private long totalPipelineExecutionTimeMs;
    private int totalDestinationsEvaluated;
    private int totalStopsScheduled;
    private double routeMatrixReusePercentage;
}
