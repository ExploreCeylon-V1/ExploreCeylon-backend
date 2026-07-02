package com.exploreceylon.backend.dto.event;

import com.exploreceylon.backend.model.Event;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class CreateEventRequest {

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    @NotNull(message = "Category is required")
    private Event.EventCategory category;

    @NotBlank(message = "Region is required")
    private String region;

    @NotBlank(message = "Location is required")
    private String location;

    private Double latitude;
    private Double longitude;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    private LocalDate endDate;

    private List<String> imageUrls;
    private Boolean isRecurring;
}
