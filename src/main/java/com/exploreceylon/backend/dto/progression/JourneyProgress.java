package com.exploreceylon.backend.dto.progression;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO capturing candidate destination progress along the travelled road route.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JourneyProgress {
    private Long destinationId;
    private double progressDistanceKm;
    private double remainingDistanceKm;
    private int routeSegmentIndex;
}
