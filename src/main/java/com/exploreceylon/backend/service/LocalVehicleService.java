package com.exploreceylon.backend.service;

import com.exploreceylon.backend.dto.Vehicle.LocalVehicleRequest;
import com.exploreceylon.backend.dto.Vehicle.LocalVehicleResponse;
import com.exploreceylon.backend.model.Vehicle;
import com.exploreceylon.backend.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LocalVehicleService {

    private final VehicleRepository vehicleRepository;

    // ── Search Vehicles ────────────────────────────────────────
    public List<LocalVehicleResponse> searchVehicles(LocalVehicleRequest request) {
        log.info("Searching local vehicles — district: {}, type: {}",
                request.getDistrict(), request.getType());

        List<Vehicle> vehicles;

        // Parse type
        Vehicle.VehicleType type = null;
        if (request.getType() != null && !request.getType().isEmpty()) {
            try {
                type = Vehicle.VehicleType.valueOf(
                        request.getType().toUpperCase());
            } catch (Exception e) {
                log.warn("Invalid vehicle type: {}", request.getType());
            }
        }

        if (request.getDistrict() != null && !request.getDistrict().isEmpty()) {
            vehicles = vehicleRepository.searchVehicles(
                    request.getDistrict(),
                    request.getMinPrice(),
                    request.getMaxPrice(),
                    type
            );
        } else {
            vehicles = vehicleRepository.findByAvailableTrue();
        }

        // Filter airport transfer
        if (Boolean.TRUE.equals(request.getAirportTransfer())) {
            vehicles = vehicles.stream()
                    .filter(v -> Boolean.TRUE.equals(v.getAirportTransfer()))
                    .collect(Collectors.toList());
        }

        // Filter driver included
        if (Boolean.TRUE.equals(request.getDriverIncluded())) {
            vehicles = vehicles.stream()
                    .filter(v -> Boolean.TRUE.equals(v.getDriverIncluded()))
                    .collect(Collectors.toList());
        }

        log.info("Found {} local vehicles", vehicles.size());
        return vehicles.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ── Get All Vehicles ───────────────────────────────────────
    public List<LocalVehicleResponse> getAllVehicles() {
        return vehicleRepository.findByAvailableTrue()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ── Get Vehicle By ID ──────────────────────────────────────
    public LocalVehicleResponse getVehicleById(Long id) {
        Vehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException(
                        "Vehicle not found with id: " + id));
        return toResponse(vehicle);
    }

    // ── Get TukTuks ───────────────────────────────────────────
    public List<LocalVehicleResponse> getTukTuks() {
        return vehicleRepository
                .findByTypeAndAvailableTrue(Vehicle.VehicleType.TUKTUK)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ── Get Airport Transfers ──────────────────────────────────
    public List<LocalVehicleResponse> getAirportTransfers() {
        return vehicleRepository
                .findByAirportTransferTrueAndAvailableTrue()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ── Add Vehicle (Admin) ────────────────────────────────────
    public LocalVehicleResponse addVehicle(Vehicle vehicle) {
        Vehicle saved = vehicleRepository.save(vehicle);
        log.info("Added new vehicle: {}", saved.getName());
        return toResponse(saved);
    }

    // ── Update Availability ────────────────────────────────────
    public void updateAvailability(Long id, Boolean available) {
        Vehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Vehicle not found"));
        vehicle.setAvailable(available);
        vehicleRepository.save(vehicle);
    }

    // ── Map Entity → Response ──────────────────────────────────
    private LocalVehicleResponse toResponse(Vehicle v) {
        LocalVehicleResponse res = new LocalVehicleResponse();
        res.setId(v.getId());
        res.setName(v.getName());
        res.setType(v.getType() != null ? v.getType().name() : "");
        res.setCategory(v.getCategory() != null ? v.getCategory().name() : "");
        res.setBrand(v.getBrand());
        res.setModel(v.getModel());
        res.setSeats(v.getSeats());
        res.setPricePerDay(v.getPricePerDay());
        res.setCurrency(v.getCurrency());
        res.setDistrict(v.getDistrict());
        res.setPickupLocation(v.getPickupLocation());
        res.setDriverIncluded(v.getDriverIncluded());
        res.setAirportTransfer(v.getAirportTransfer());
        res.setDriverName(v.getDriverName());
        res.setDriverPhone(v.getDriverPhone());
        res.setDriverLanguages(v.getDriverLanguages());
        res.setDescription(v.getDescription());
        res.setImageUrls(v.getImageUrls());
        res.setRating(v.getRating());
        res.setReviewCount(v.getReviewCount());
        res.setAvailable(v.getAvailable());
        return res;
    }
}