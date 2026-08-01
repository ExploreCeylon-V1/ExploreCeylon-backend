package com.exploreceylon.backend.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleStatsResponse {

    private long totalVehicles;
    private long availableVehicles;
    private long bookedVehicles;
    private double totalRevenue;
    private double totalCommission;
}