package com.exploreceylon.backend.dto.admin;

import com.exploreceylon.backend.dto.guide.GuideBookingResponse;
import com.exploreceylon.backend.dto.vehicle.VehicleBookingResponse;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AllBookingsResponse {
    private List<VehicleBookingResponse> vehicleBookings;
    private List<GuideBookingResponse>   guideBookings;
    private Long totalVehicleBookings;
    private Long totalGuideBookings;
    private Long totalBookings;
}