package com.exploreceylon.backend.dto.destination;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DestinationReviewResponse {
    private Long id;
    private Long destinationId;
    private String travelerName;
    private Integer rating;
    private String comment;
    private LocalDateTime createdAt;
}