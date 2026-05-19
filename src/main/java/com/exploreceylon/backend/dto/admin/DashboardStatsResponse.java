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
}