package com.exploreceylon.backend.dto.trip;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncableBookingResponse {
    private Long bookingId;
    private String bookingType;         // "VEHICLE" | "GUIDE"
    private String referenceId;         // "VB-" + id | "GB-" + id
    private String providerName;
    private String providerImage;
    private String providerPhone;
    private String providerEmail;
    private String vehicleNumber;
    private LocalDate startDate;
    private LocalDate endDate;
    private Double totalCost;
    private Double advanceAmount;
    private Double balanceAmount;
    private String currency;
    private String status;              // "CONFIRMED" | "COMPLETED"
    private boolean isSynced;           // true if already in this trip's budget items
    private Long tripId;
    private String tripTitle;
    private String pickupLocation;
    private String notes;
}
