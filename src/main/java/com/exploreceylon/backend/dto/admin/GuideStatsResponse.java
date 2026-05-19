package com.exploreceylon.backend.dto.admin;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GuideStatsResponse {
    private Long totalGuides;
    private Long availableGuides;
    private Long bookedToday;
    private Double totalRevenue;
    private Double totalCommission;
}