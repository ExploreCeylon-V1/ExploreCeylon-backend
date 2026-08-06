package com.exploreceylon.backend.dto.routing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result DTO returned by DistanceCalculator containing road distance,
 * estimated travel duration, road geometry, provider flag, and status.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DistanceResult {
    private double drivingDistanceKm;
    private int drivingDurationMinutes;
    private String encodedPolyline;
    private String providerUsed;
    private boolean success;
}
