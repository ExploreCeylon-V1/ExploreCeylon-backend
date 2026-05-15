package com.exploreceylon.backend.dto.trip;

import com.exploreceylon.backend.model.Trip;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class GenerateAiTripRequest {

    @NotNull(message = "Trip ID is required")
    private Long tripId;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    private LocalDate endDate;

    private Trip.TravelStyle travelStyle;
    private Trip.BudgetRange budgetRange;

    @NotNull(message = "Group size is required")
    private Integer groupSize;

    private List<String> regions;
    private List<String> interests;
    private String startingPoint;
    private String specialNotes;
}