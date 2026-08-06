package com.exploreceylon.backend.dto.planner;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for generating and saving a persistent trip.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlannerSaveRequest {

    @NotNull(message = "Planner request details are required")
    @Valid
    private PlannerRequest plannerRequest;

    private String customTripTitle;
    private Boolean autoConfirm;
}
