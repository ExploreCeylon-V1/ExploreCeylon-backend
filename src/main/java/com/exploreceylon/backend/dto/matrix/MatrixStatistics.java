package com.exploreceylon.backend.dto.matrix;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO capturing execution and performance statistics for RouteMatrix computations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatrixStatistics {
    private int locationCount;
    private long buildTimeMs;
    private String providerUsed;
    private boolean isCacheHit;
}
