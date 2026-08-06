package com.exploreceylon.backend.dto.planner;

import com.exploreceylon.backend.dto.cost.TripCostEstimate;
import com.exploreceylon.backend.dto.narrative.NarrativeResponse;
import com.exploreceylon.backend.dto.recommendation.RecommendedGem;
import com.exploreceylon.backend.model.Event;
import com.exploreceylon.backend.model.Trip.TripStatus;
import com.exploreceylon.backend.service.ItineraryAssemblyService.PlannedDay;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Unified Response DTO for End-to-End Trip Planning API.
 * Exposes estimatedCost for Phase 9 AI estimation while leaving manual Budget Tracker independent.
 * Supports persistence fields: tripId, createdAt, owner, and status.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlannerResponse {
    private Long tripId;
    private LocalDateTime createdAt;
    private String owner;
    private TripStatus status;
    private PlannerSummary summary;
    private List<PlannedDay> days;
    private List<String> timeline;
    private List<String> destinations;
    private List<RecommendedGem> gems;
    private List<Event> events;
    private NarrativeResponse narrative;
    private TripCostEstimate estimatedCost;
    private PlannerStatistics statistics;
    private double qualityScore;
}
