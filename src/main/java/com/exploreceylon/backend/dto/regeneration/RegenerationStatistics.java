package com.exploreceylon.backend.dto.regeneration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Statistics DTO capturing regeneration execution performance and matrix reuse.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegenerationStatistics {
    private int affectedDaysCount;
    private int totalDaysCount;
    private long executionTimeMs;
    private double matrixReusedPercentage;
    private boolean narrativeRegenerated;
    private boolean costRegenerated;
}
