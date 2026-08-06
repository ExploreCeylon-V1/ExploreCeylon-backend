package com.exploreceylon.backend.dto.planner;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * High-level trip summary DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlannerSummary {
    private String origin;
    private String destination;
    private int tripDays;
    private String travelStyle;
    private String budget;
    private int groupSize;
    private double overallScore;
}
