package com.exploreceylon.backend.dto.admin;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

// Unified row shape for the admin Review Moderation table — one entry
// covers a DestinationReview, Gemreview, GuideReview, or VehicleReview,
// discriminated by `entityType`. Destination/Gem reviews carry only a
// free-text travelerName (no account link); Guide/Vehicle reviews are
// written by a real logged-in user — reviewerUserId is null for the former.
@Data
@Builder
public class AdminReviewResponse {
    private Long id;
    private String entityType; // DESTINATION | GEM | GUIDE | VEHICLE
    private Long entityId;
    private String entityName;
    private Long reviewerUserId;
    private String reviewerName;
    private Integer rating;
    private String comment;
    private LocalDateTime createdAt;
}
