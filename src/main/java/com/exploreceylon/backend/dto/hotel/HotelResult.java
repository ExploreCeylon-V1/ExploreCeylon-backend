package com.exploreceylon.backend.dto.hotel;

import lombok.Data;

import java.util.List;

@Data
public class HotelResult {
    private String hotelId;
    private String name;
    private String address;
    private double reviewScore;
    private String reviewScoreWord;
    private int reviewsCount;
    private double pricePerNight;
    private String currency;
    private String photoUrl;
    private int stars;
    private boolean isLocalPick; // Sri Lankan boutique stays
    private String propertyType;      // e.g. "Hotel", "Resort", "Apartment"
    private List<String> amenities;   // e.g. ["Free WiFi", "Pool"], may be empty
    private Double distanceFromCenterKm; // null if not provided by API
    private String freeCancellationUntil; // null if non-refundable / not provided
}