package com.exploreceylon.backend.dto.Vehicle;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;

@Data
public class BookVehicleRequest {

    @NotNull(message = "Vehicle ID is required")
    private Long vehicleId;

    private Long tripId;

    @NotNull(message = "Pickup date is required")
    private LocalDate pickupDate;

    @NotNull(message = "Dropoff date is required")
    private LocalDate dropoffDate;

    private String pickupTime;
    private String dropoffTime;

    @NotBlank(message = "Pickup location is required")
    private String pickupLocation;

    private String dropoffLocation;
    private String notes;
}
