package com.exploreceylon.backend.dto.planner;

import com.exploreceylon.backend.model.Trip.TripStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Summary DTO representing a persistent user-owned trip.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlannerTripSummary {
    private Long tripId;
    private String title;
    private String fromLocation;
    private String toLocation;
    private LocalDate startDate;
    private LocalDate endDate;
    private TripStatus status;
    private Double estimatedBudget;
    private String shareToken;
    private LocalDateTime createdAt;
}
