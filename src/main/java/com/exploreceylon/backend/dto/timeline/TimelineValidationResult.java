package com.exploreceylon.backend.dto.timeline;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO capturing validation audit results for scheduled timelines.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimelineValidationResult {
    private boolean isValid;
    private List<String> validationIssues;
    private int closingViolationsCount;
    private int overlapCount;
    private int mealMissedCount;
    private double qualityScore;
}
