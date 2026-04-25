package com.exploreceylon.backend.dto.hotel;

import lombok.Data;

@Data
public class HotelSearchRequest {
    private String location;
    private String checkinDate;   // format: 2025-06-01
    private String checkoutDate;  // format: 2025-06-05
    private int adults;
    private int rooms;
    private String currency;      // USD, LKR, EUR
}
