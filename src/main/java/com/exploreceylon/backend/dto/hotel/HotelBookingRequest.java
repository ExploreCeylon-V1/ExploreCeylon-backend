package com.exploreceylon.backend.dto.hotel;

import lombok.Data;

@Data
public class HotelBookingRequest {

    // Which trip + day to attach this booking to
    private Long tripId;
    private Long tripDayId;        // nullable — user can book without assigning to a day yet

    // Hotel snapshot data from the search result card
    // (frontend sends these back so we don't need another API call)
    private String hotelApiId;
    private String hotelName;
    private String hotelAddress;
    private String photoUrl;
    private Integer stars;
    private Double reviewScore;
    private String reviewScoreWord;

    // Dates
    private String checkIn;        // "2025-06-01"
    private String checkOut;       // "2025-06-05"
    private Integer adults;
    private Integer rooms;

    // Pricing
    private Double pricePerNight;
    private Double totalCost;
    private String currency;
}