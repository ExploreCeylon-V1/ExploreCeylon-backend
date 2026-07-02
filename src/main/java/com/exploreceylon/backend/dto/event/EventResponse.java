package com.exploreceylon.backend.dto.event;

import com.exploreceylon.backend.model.Event;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
@Data
public class EventResponse {
    private Long id;
    private String title;
    private String description;
    private Event.EventCategory category;
    private String region;
    private String location;
    private Double latitude;
    private Double longitude;
    private LocalDate startDate;
    private LocalDate endDate;
    private List<String> imageUrls;
    private Boolean isRecurring;
    private LocalDateTime createdAt;
}
