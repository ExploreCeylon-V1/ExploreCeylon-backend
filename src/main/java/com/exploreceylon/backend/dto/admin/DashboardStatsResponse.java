package com.exploreceylon.backend.dto.admin;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardStatsResponse {
    // Main stats
    private Long totalUsers;
    private Long totalBookings;
    private Double totalRevenue;
    private Long activeTrips;

    // Secondary stats
    private Long totalVehicles;
    private Long totalGuides;
    private Long totalDestinations;
    private Long totalGems;
    private Long totalEvents;

    // Booking breakdown
    private Long vehicleBookings;
    private Long guideBookings;
    private Long pendingBookings;

    // Revenue
    private Double vehicleRevenue;
    private Double guideRevenue;
    private Double totalCommission;

    // User health (Phase 2C)
    private Long activeUsers;
    private Long verifiedUsers;
    private Long newUsersLast30Days;

    // Content/engagement (Phase 2C)
    private Long tripsCreated;
    private Long totalReviews;
    // Always 0 — the review schema has no moderation/"pending" flag on any
    // of the 4 review tables (see Phase 1 audit). Kept in the response
    // rather than omitted so the dashboard card has a real, honest value
    // instead of silently disappearing if a future schema change adds one.
    private Long pendingReviews;
}
