package com.exploreceylon.backend.controller;

import com.exploreceylon.backend.dto.vehicle.LocalVehicleRequest;
import com.exploreceylon.backend.dto.vehicle.LocalVehicleResponse;
import com.exploreceylon.backend.service.LocalVehicleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/vehicles/local")
@RequiredArgsConstructor
public class LocalVehicleController {

    private final LocalVehicleService localVehicleService;

    // GET /api/v1/vehicles/local
    @GetMapping
    public ResponseEntity<List<LocalVehicleResponse>> getAllVehicles() {
        return ResponseEntity.ok(localVehicleService.getAllVehicles());
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
    public ResponseEntity<List<LocalVehicleResponse>> getTukTuks() {
        return ResponseEntity.ok(localVehicleService.getTukTuks());
    }

    // GET /api/v1/vehicles/local/airport-transfers
    @GetMapping("/airport-transfers")
    public ResponseEntity<List<LocalVehicleResponse>> getAirportTransfers() {
        return ResponseEntity.ok(
                localVehicleService.getAirportTransfers());
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