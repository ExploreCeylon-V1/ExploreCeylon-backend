package com.exploreceylon.backend.dto.admin;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class RecentActivityResponse {
    private List<Registration> recentRegistrations;
    private List<TripActivity> recentTrips;
    private List<AdminBookingResponse> recentBookings;
    private List<AdminReviewResponse> recentReviews;

    @Data
    @Builder
    public static class Registration {
        private Long id;
        private String name;
        private String email;
        private LocalDateTime createdAt;
    }

    @Data
    @Builder
    public static class TripActivity {
        private Long id;
        private String title;
        private String userName;
        private LocalDateTime createdAt;
    }
}
