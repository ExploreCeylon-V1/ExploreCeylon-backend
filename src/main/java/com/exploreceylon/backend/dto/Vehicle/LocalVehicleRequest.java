package com.exploreceylon.backend.dto.Vehicle;

import lombok.Data;

@Data
public class LocalVehicleRequest {
    private String district;
    private String type;      // CAR, VAN, SUV, TUKTUK, SCOOTER
    private Double minPrice;
    private Double maxPrice;
    private Boolean driverIncluded;
    private Boolean airportTransfer;
}