package com.exploreceylon.backend.dto.trip;

import lombok.Data;
import java.time.LocalDate;
import java.util.List;

@Data
public class TripDayResponse {
    private Long id;
    private Integer dayNumber;
    private LocalDate date;
    private String region;
    private String theme;
    private String tips;
    private Long festivalEventId;
    private Double estimatedDayCost;
    private List<TripDayItemResponse> items;
}