package com.exploreceylon.backend.dto.matrix;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO holding single point-to-point distance and duration lookup results.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteMatrixEntry {
    private double distanceKm;
    private double durationMinutes;
    private String providerUsed;
}
