package com.exploreceylon.backend.dto.trip;

import com.exploreceylon.backend.model.Trip;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class CreateTripRequest {

    @NotBlank(message = "Title is required")
    private String title;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    private LocalDate endDate;

    private Trip.TravelStyle travelStyle;
    private Trip.BudgetRange budgetRange;
    private Integer groupSize;

    // Preferences
    private List<String> regions;
    private List<String> interests;
    private String startingPoint;
    private String specialNotes;

    // AI generate?
    private Boolean generateWithAi = false;
}