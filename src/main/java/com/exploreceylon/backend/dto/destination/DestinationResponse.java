package com.exploreceylon.backend.dto.destination;

import com.exploreceylon.backend.model.Destination;
import lombok.Data;
import java.util.List;

@Data
public class DestinationResponse {
    private Long id;
    private String name;
    private String district;
    private String province;
    private String description;
    private String shortDescription;
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
    private Boolean active;
    private Double rating;
    private Integer reviewCount;
    private String unescoStatus;
    private String nearbyGems;
}