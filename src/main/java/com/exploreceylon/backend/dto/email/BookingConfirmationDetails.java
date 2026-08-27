package com.exploreceylon.backend.dto.email;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingConfirmationDetails {
    private Long bookingId;
    private String referenceId;
    private String bookingType;         // "VEHICLE" | "GUIDE"
    private String customerName;
    private String customerEmail;
    private String customerPhone;

    private String providerName;
    private String providerPhone;
    private String providerWhatsapp;
    private String providerEmail;
    private String providerDistrict;

    // Vehicle specific
    private String vehicleNumber;       // licensePlate
    private String vehicleType;
    private String vehicleModel;
    private String pickupTime;
    private String dropoffTime;
    private String pickupLocation;
    private String dropoffLocation;

    // Dates
    private LocalDate startDate;
    private LocalDate endDate;

    // Notes / Special Requirements
    private String notes;

    // Financials
    private Double totalCost;
    private Double advanceAmount;
    private Double balanceAmount;
    private String currency;

    // Related Trip
    private Long tripId;
    private String tripTitle;
}
