package com.exploreceylon.backend.service.ranking;

import lombok.Builder;
import lombok.Data;

import java.util.Set;

/**
 * Internal breakdown and explanation object for destination priority scoring.
 * For internal debugging, logging, and performance analysis only.
 * MUST NOT be exposed through public REST API endpoints.
 */
@Data
@Builder
public class DestinationScore {
    private Long destinationId;
    private String destinationName;
    private double finalScore;

    // Individual score component breakdown
    private double ratingScore;
    private double popularityScore;
    private double styleScore;
    private double seasonScore;
    private double bonusScore;
    private double proximityScore;

    // Detailed explanation metadata
    private Set<String> matchingStyles;
    private boolean seasonMatch;
    private Double distanceKm;
}
