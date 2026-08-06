package com.exploreceylon.backend.dto.planner;

import com.exploreceylon.backend.model.Trip.TripStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO returned after generating and persisting a trip.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlannerSaveResponse {
    private Long tripId;
    private String shareToken;
    private TripStatus status;
    private LocalDateTime createdAt;
    private PlannerResponse plannerResponse;
}
