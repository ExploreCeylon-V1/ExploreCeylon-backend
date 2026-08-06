package com.exploreceylon.backend.dto.planner;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Unified Request DTO for End-to-End Trip Planning API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlannerRequest {

    @NotBlank(message = "Origin starting location is required")
    private String origin;

    @NotBlank(message = "Destination location is required")
    private String destination;

    @Min(value = 1, message = "Trip duration must be at least 1 day")
    private int tripDays;

    private String budget;            // e.g. "MID_RANGE", "BUDGET", "LUXURY"
    private String travelStyle;       // e.g. "RELAXED", "BALANCED", "FAST_PACED"

    @Min(value = 1, message = "Group size must be at least 1 person")
    private int groupSize;

    private LocalDate startDate;
    private List<String> preferences;
    private List<String> avoidLocations;
    private String specialNotes;
}
