package com.exploreceylon.backend.controller;

import com.exploreceylon.backend.dto.vehicle.CreateVehicleReviewRequest;
import com.exploreceylon.backend.dto.vehicle.LocalVehicleRequest;
import com.exploreceylon.backend.dto.vehicle.LocalVehicleResponse;
import com.exploreceylon.backend.dto.vehicle.VehicleReviewResponse;
import com.exploreceylon.backend.service.LocalVehicleService;
import com.exploreceylon.backend.service.VehicleBookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/vehicles/local")
@RequiredArgsConstructor
public class LocalVehicleController {

    private final LocalVehicleService localVehicleService;
    private final VehicleBookingService vehicleBookingService;

    // GET /api/v1/vehicles/local
    // startDate/endDate (optional, both required together) exclude vehicles
    // with an active booking overlapping that range.
    @GetMapping
    public ResponseEntity<List<LocalVehicleResponse>> getAllVehicles(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(localVehicleService.getAllVehicles(startDate, endDate));
    }

    // GET /api/v1/vehicles/local/{id}
    @GetMapping("/{id}")
    public ResponseEntity<LocalVehicleResponse> getVehicleById(
            @PathVariable Long id) {
        return ResponseEntity.ok(localVehicleService.getVehicleById(id));
    }

    // POST /api/v1/vehicles/local/search
    @PostMapping("/search")
    public ResponseEntity<List<LocalVehicleResponse>> searchVehicles(
            @RequestBody LocalVehicleRequest request) {
        return ResponseEntity.ok(
                localVehicleService.searchVehicles(request));
    }

    // GET /api/v1/vehicles/local/tuktuks
    @GetMapping("/tuktuks")
    public ResponseEntity<List<LocalVehicleResponse>> getTukTuks(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(localVehicleService.getTukTuks(startDate, endDate));
    }

    // GET /api/v1/vehicles/local/airport-transfers
    @GetMapping("/airport-transfers")
    public ResponseEntity<List<LocalVehicleResponse>> getAirportTransfers(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(
                localVehicleService.getAirportTransfers(startDate, endDate));
    }

    // GET /api/v1/vehicles/local/{id}/check-availability?startDate&endDate → {available}
    @GetMapping("/{id}/check-availability")
    public ResponseEntity<Map<String, Boolean>> checkAvailability(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        boolean available = vehicleBookingService.checkAvailability(id, startDate, endDate);
        return ResponseEntity.ok(Map.of("available", available));
    }

    // ── Review Endpoints ───────────────────────────────────

    // GET /api/v1/vehicles/local/{id}/reviews
    @GetMapping("/{id}/reviews")
    public ResponseEntity<List<VehicleReviewResponse>> getReviews(
            @PathVariable Long id) {
        return ResponseEntity.ok(localVehicleService.getVehicleReviews(id));
    }

    // POST /api/v1/vehicles/local/{id}/reviews
    @PostMapping("/{id}/reviews")
    public ResponseEntity<VehicleReviewResponse> writeReview(
            @PathVariable Long id,
            @Valid @RequestBody CreateVehicleReviewRequest request) {
        return ResponseEntity.ok(localVehicleService.writeReview(id, request));
    }

    // PUT /api/v1/vehicles/local/{id}/availability
    @PutMapping("/{id}/availability")
    public ResponseEntity<Void> updateAvailability(
            @PathVariable Long id,
            @RequestParam Boolean available) {
        localVehicleService.updateAvailability(id, available);
        return ResponseEntity.ok().build();
    }
}