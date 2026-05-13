package com.exploreceylon.backend.dto.guide;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;

@Data
public class BookGuideRequest {

    @NotNull(message = "Guide ID is required")
    private Long guideId;

    private Long tripId;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    private LocalDate endDate;

    private String notes;
}