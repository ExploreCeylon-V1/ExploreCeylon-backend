package com.exploreceylon.backend.dto.admin;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

// Unified row shape for the admin Booking Management table — one entry
// covers either a VehicleBooking or a GuideBooking, discriminated by `type`.
// Kept type-extensible in the AdminService merge logic; add a new `type`
// value there (not here) if a new booking domain is introduced later.
@Data
@Builder
public class AdminBookingResponse {
    private Long id;
    private String type; // VEHICLE | GUIDE
    private String status;
    private Long customerId;
    private String customerName;
    private String customerEmail;
    private Long providerId;
    private String providerName;
    private Long tripId;
    private LocalDate startDate;
    private LocalDate endDate;
    private Double totalCost;
    private LocalDateTime createdAt;
}
