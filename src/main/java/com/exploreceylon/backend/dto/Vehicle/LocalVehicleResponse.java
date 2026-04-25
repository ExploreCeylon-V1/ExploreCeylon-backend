package com.exploreceylon.backend.dto.Vehicle;

import lombok.Data;
import java.util.List;

@Data
public class LocalVehicleResponse {
    private Long id;
    private String name;
    private String type;
    private String category;
    private String brand;
    private String model;
    private Integer seats;
    private Double pricePerDay;
    private String currency;
    private String district;
    private String pickupLocation;
    private Boolean driverIncluded;
    private Boolean airportTransfer;
    private String driverName;
    private String driverPhone;
    private String driverLanguages;
    private String description;
    private List<String> imageUrls;
    private Double rating;
    private Integer reviewCount;
    private Boolean available;
}