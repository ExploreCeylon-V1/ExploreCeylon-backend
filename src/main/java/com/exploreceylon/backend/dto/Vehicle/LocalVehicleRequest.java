package com.exploreceylon.backend.dto.vehicle;

import lombok.Data;

import java.time.LocalDate;

@Data
public class LocalVehicleRequest {
    private String district;
    private String type;      // CAR, VAN, SUV, TUKTUK, SCOOTER
    private Double minPrice;
    private Double maxPrice;
    private Boolean driverIncluded;

    // When both given, excludes vehicles with an active booking overlapping the range.
    private LocalDate startDate;
    private LocalDate endDate;
}