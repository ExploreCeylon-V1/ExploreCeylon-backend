package com.exploreceylon.backend.dto.admin;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VehicleStatsResponse {
    private Long totalVehicles;
    private Long availableVehicles;
    private Long bookedVehicles;
    private Double totalRevenue;
    private Double totalCommission;
}