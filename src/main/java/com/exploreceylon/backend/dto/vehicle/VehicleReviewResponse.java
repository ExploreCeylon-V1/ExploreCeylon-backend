package com.exploreceylon.backend.dto.vehicle;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class VehicleReviewResponse {
    private Long id;
    private Long vehicleId;
    private Long userId;
    private String userName;
    private Integer rating;
    private String comment;
    private LocalDateTime createdAt;
}
