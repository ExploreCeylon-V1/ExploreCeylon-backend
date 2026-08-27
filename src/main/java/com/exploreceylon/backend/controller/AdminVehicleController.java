package com.exploreceylon.backend.controller;

import com.exploreceylon.backend.dto.vehicle.AdminVehicleRequest;
import com.exploreceylon.backend.dto.vehicle.LocalVehicleResponse;
import com.exploreceylon.backend.model.Vehicle;
import com.exploreceylon.backend.service.LocalVehicleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/vehicles")
@RequiredArgsConstructor
public class AdminVehicleController {

    private final LocalVehicleService localVehicleService;

    @PostMapping
    public ResponseEntity<LocalVehicleResponse> createVehicle(
            @RequestBody AdminVehicleRequest request) {
        Vehicle vehicle = buildVehicleFromRequest(request, new Vehicle());
        LocalVehicleResponse saved = localVehicleService.addVehicle(vehicle);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<LocalVehicleResponse> updateVehicle(
            @PathVariable Long id,
            @RequestBody AdminVehicleRequest request) {
        Vehicle vehicle = buildVehicleFromRequest(request, new Vehicle());
        LocalVehicleResponse updated = localVehicleService.updateVehicle(id, vehicle);
        return ResponseEntity.ok(updated);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Void> patchVehicleAvailability(
            @PathVariable Long id,
            @RequestBody AdminVehicleRequest request) {
        if (request.getAvailable() == null) {
            return ResponseEntity.badRequest().build();
        }
        localVehicleService.updateAvailability(id, request.getAvailable());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteVehicle(@PathVariable Long id) {
        localVehicleService.deleteVehicle(id);
        return ResponseEntity.ok().build();
    }

    private Vehicle buildVehicleFromRequest(AdminVehicleRequest request, Vehicle vehicle) {
        if (request.getName() != null) {
            vehicle.setName(request.getName());
        }
        if (request.getType() != null) {
            try {
                vehicle.setType(Vehicle.VehicleType.valueOf(request.getType().toUpperCase()));
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (request.getCategory() != null) {
            try {
                vehicle.setCategory(Vehicle.VehicleCategory.valueOf(request.getCategory().toUpperCase()));
            } catch (IllegalArgumentException ignored) {
            }
        }
        vehicle.setBrand(request.getBrand());
        vehicle.setModel(request.getModel());
        vehicle.setYear(request.getYear());
        vehicle.setSeats(request.getSeats());
        vehicle.setColor(request.getColor());
        vehicle.setLicensePlate(request.getLicensePlate());
        vehicle.setPricePerDay(request.getPricePerDay());
        vehicle.setCurrency(request.getCurrency());
        vehicle.setDistrict(request.getDistrict());
        vehicle.setPickupLocation(request.getPickupLocation());
        vehicle.setLatitude(request.getLatitude());
        vehicle.setLongitude(request.getLongitude());
        vehicle.setDriverName(request.getDriverName());
        vehicle.setDriverPhone(request.getDriverPhone());
        vehicle.setWhatsappNumber(request.getWhatsappNumber());
        vehicle.setDriverLanguages(request.getDriverLanguages());
        vehicle.setEmail(request.getEmail());
        vehicle.setDriverIncluded(request.getDriverIncluded());
        vehicle.setAvailable(request.getAvailable());
        vehicle.setDescription(request.getDescription());
        vehicle.setImageUrls(request.getImageUrls());
        return vehicle;
    }
}
