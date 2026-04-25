package com.exploreceylon.backend.dto.hotel;

import lombok.Data;

@Data
public class HotelResult {
    private String hotelId;
    private String name;
    private String address;
    private double reviewScore;
    private String reviewScoreWord;
    private double pricePerNight;
    private String currency;
    private String photoUrl;
    private int stars;
    private boolean isLocalPick; // Sri Lankan boutique stays
}