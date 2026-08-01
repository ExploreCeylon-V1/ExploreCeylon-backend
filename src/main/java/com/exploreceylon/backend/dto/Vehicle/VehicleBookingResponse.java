package com.exploreceylon.backend.dto.vehicle;

import com.exploreceylon.backend.model.VehicleBooking;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class VehicleBookingResponse {
    private Long id;
    private Long vehicleId;
    private String vehicleName;
    private String vehicleType;
    private String driverName;
    private Long userId;
    private Long tripId;
    private LocalDate pickupDate;
    private LocalDate dropoffDate;
    private String pickupTime;
    private String dropoffTime;
    private String pickupLocation;
    private String dropoffLocation;
    private VehicleBooking.BookingStatus status;
    private Double totalCost;
    private Double advanceAmount;
    private Double balanceAmount;
    private String notes;
    private LocalDateTime createdAt;
}