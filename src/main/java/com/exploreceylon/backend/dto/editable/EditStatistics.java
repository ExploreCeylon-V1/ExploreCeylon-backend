package com.exploreceylon.backend.dto.editable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Statistics DTO capturing localized re-optimization performance and matrix reuse metrics.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EditStatistics {
    private int affectedDaysCount;
    private int recomputedStopsCount;
    private long executionTimeMs;
    private double matrixReusePercentage;
}
