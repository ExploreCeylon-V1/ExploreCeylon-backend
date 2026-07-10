package com.exploreceylon.backend.dto.admin;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class BulkReviewDeleteRequest {
    @NotEmpty
    private List<ReviewRef> items;

    @Data
    public static class ReviewRef {
        private String entityType; // DESTINATION | GEM | GUIDE | VEHICLE
        private Long id;
    }
}
