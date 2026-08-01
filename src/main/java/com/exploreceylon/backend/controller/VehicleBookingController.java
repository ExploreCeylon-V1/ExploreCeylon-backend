package com.exploreceylon.backend.controller;

import com.exploreceylon.backend.dto.vehicle.BookVehicleRequest;
import com.exploreceylon.backend.dto.vehicle.VehicleBookingResponse;
import com.exploreceylon.backend.service.VehicleBookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/vehicle-bookings")
@RequiredArgsConstructor
public class VehicleBookingController {

    private final VehicleBookingService bookingService;

    // POST /api/v1/vehicle-bookings
    @PostMapping
    public ResponseEntity<VehicleBookingResponse> bookVehicle(
            @Valid @RequestBody BookVehicleRequest request) {
        return ResponseEntity.ok(
                bookingService.bookVehicle(request));
    }

    // GET /api/v1/vehicle-bookings/my
    @GetMapping("/my")
    public ResponseEntity<List<VehicleBookingResponse>> getMyBookings() {
        return ResponseEntity.ok(bookingService.getMyBookings());
    }

    // GET /api/v1/vehicle-bookings/{id}
    @GetMapping("/{id}")
    public ResponseEntity<VehicleBookingResponse> getBookingById(
            @PathVariable Long id) {
        return ResponseEntity.ok(bookingService.getBookingById(id));
    }

    // GET /api/v1/vehicle-bookings/trip/{tripId}
    @GetMapping("/trip/{tripId}")
    public ResponseEntity<List<VehicleBookingResponse>> getTripBookings(
            @PathVariable Long tripId) {
        return ResponseEntity.ok(
                bookingService.getTripBookings(tripId));
    }

    // GET /api/v1/vehicle-bookings/check-availability
    @GetMapping("/check-availability")
    public ResponseEntity<Boolean> checkAvailability(
            @RequestParam Long vehicleId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate pickupDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate dropoffDate) {
        return ResponseEntity.ok(
                bookingService.checkAvailability(
                        vehicleId, pickupDate, dropoffDate));
    }

    // PATCH /api/v1/vehicle-bookings/{id}/cancel
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<VehicleBookingResponse> cancelBooking(
            @PathVariable Long id) {
        return ResponseEntity.ok(bookingService.cancelBooking(id));
    }

    // PATCH /api/v1/vehicle-bookings/{id}/status (Admin)
    @PatchMapping("/{id}/status")
    public ResponseEntity<VehicleBookingResponse> updateStatus(
            @PathVariable Long id,
            @RequestParam String status) {
        return ResponseEntity.ok(
                bookingService.updateStatus(id, status));
    }
}