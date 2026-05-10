package com.exploreceylon.backend.dto.event;

import com.exploreceylon.backend.model.Event;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class EventResponse {
    private Long id;
    private String title;
    private String description;
    private Event.EventCategory category;
    private String region;
    private LocalDate startDate;
    private LocalDate endDate;
    private String imageUrl;
    private Boolean isRecurring;
    private LocalDateTime createdAt;
}