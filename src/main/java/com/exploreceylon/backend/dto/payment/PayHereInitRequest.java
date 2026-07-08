package com.exploreceylon.backend.dto.payment;

import lombok.Data;

@Data
public class PayHereInitRequest {

    // Who is paying
    private Long bookingId;
    private String bookingType;   // "GUIDE" or "VEHICLE"
    private String paymentPhase;  // "ADVANCE" or "FINAL"

    // Customer info (PayHere needs these)
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String address;
    private String city;
    private String country;
}