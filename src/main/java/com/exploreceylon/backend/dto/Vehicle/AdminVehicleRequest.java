package com.exploreceylon.backend.dto.vehicle;

import lombok.Data;

import java.util.List;

@Data
public class AdminVehicleRequest {
    private String name;
    private String type;
    private String category;
    private String brand;
    private String model;
    private Integer year;
    private Integer seats;
    private String color;
    private String licensePlate;
    private Double pricePerDay;
    private String currency;
    private String district;
    private String pickupLocation;
    private Double latitude;
    private Double longitude;
    private String driverName;
    private String driverPhone;
    private String whatsappNumber;
    private String driverLanguages;
    private String email;
    private Boolean driverIncluded;
    private Boolean available;
    private String description;
    private List<String> imageUrls;
}
