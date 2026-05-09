package com.exploreceylon.backend.dto.trip;

import com.exploreceylon.backend.model.Trip;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class TripResponse {
    private Long id;
    private String title;
    private LocalDate startDate;
    private LocalDate endDate;
    private Trip.TravelStyle travelStyle;
    private Trip.BudgetRange budgetRange;
    private Integer groupSize;
    private Trip.TripStatus status;
    private Boolean aiGenerated;
    private String shareToken;
    private List<TripDayResponse> days;
    private LocalDateTime createdAt;

    // Preferences
    private String regions;
    private String interests;
    private String startingPoint;
}