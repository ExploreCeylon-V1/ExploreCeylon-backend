package com.exploreceylon.backend.dto.destination;

import com.exploreceylon.backend.model.Destination;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

@Data
public class CreateDestinationRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "District is required")
    private String district;

    @NotBlank(message = "Province is required")
    private String province;

    private String description;
    private String shortDescription;

    @NotNull(message = "Category is required")
    private Destination.DestinationCategory category;

    private String bestMonths;
    private String activities;
    private Double latitude;
    private Double longitude;
    private String coverImageUrl;
    private List<String> imageUrls;
    private String travelTimeFrom;
    private String entryFee;
    private String openingHours;
    private Boolean featured;
    private String unescoStatus;
    private String nearbyGems;
}