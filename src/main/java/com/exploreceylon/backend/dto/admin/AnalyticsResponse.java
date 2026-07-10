package com.exploreceylon.backend.dto.admin;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AnalyticsResponse {
    private List<MonthPoint> monthlyRegistrations;
    private List<MonthPoint> monthlyTrips;
    private List<MonthlyBookings> monthlyBookings;
    private List<RatingCount> reviewDistribution;
    private List<TopListsResponse.TopDestination> destinationPopularity;

    @Data
    @Builder
    public static class MonthPoint {
        private String month; // "2026-01"
        private Long count;
    }

    @Data
    @Builder
    public static class MonthlyBookings {
        private String month;
        private Long vehicleCount;
        private Long guideCount;
    }

    @Data
    @Builder
    public static class RatingCount {
        private Integer rating;
        private Long count;
    }
}
