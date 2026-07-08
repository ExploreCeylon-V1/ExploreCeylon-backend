package com.exploreceylon.backend.service;

import com.exploreceylon.backend.dto.vehicle.CreateVehicleReviewRequest;
import com.exploreceylon.backend.dto.vehicle.LocalVehicleRequest;
import com.exploreceylon.backend.dto.vehicle.LocalVehicleResponse;
import com.exploreceylon.backend.dto.vehicle.VehicleReviewResponse;
import com.exploreceylon.backend.model.User;
import com.exploreceylon.backend.model.Vehicle;
import com.exploreceylon.backend.model.VehicleBooking;
import com.exploreceylon.backend.model.VehicleReview;
import com.exploreceylon.backend.repository.VehicleBookingRepository;
import com.exploreceylon.backend.repository.VehicleRepository;
import com.exploreceylon.backend.repository.VehicleReviewRepository;
import com.exploreceylon.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LocalVehicleService {

    private final VehicleRepository        vehicleRepository;
    private final VehicleReviewRepository  reviewRepository;
    private final VehicleBookingRepository bookingRepository;
    private final UserRepository           userRepository;

    // ── Current User ───────────────────────────────────────
    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext()
                .getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

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
            vehicles = vehicleRepository.findAll();
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

        vehicles = filterByAvailability(vehicles, request.getStartDate(), request.getEndDate());

        log.info("Found {} local vehicles", vehicles.size());
        return vehicles.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ── Get All Vehicles ───────────────────────────────────────
    public List<LocalVehicleResponse> getAllVehicles() {
        return getAllVehicles(null, null);
    }

    // When startDate/endDate are both given, vehicles with an active booking
    // overlapping that range are excluded from the results.
    public List<LocalVehicleResponse> getAllVehicles(LocalDate startDate, LocalDate endDate) {
        List<Vehicle> vehicles = filterByAvailability(vehicleRepository.findAll(), startDate, endDate);
        return vehicles.stream()
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
        return getTukTuks(null, null);
    }

    public List<LocalVehicleResponse> getTukTuks(LocalDate startDate, LocalDate endDate) {
        List<Vehicle> vehicles = filterByAvailability(
                vehicleRepository.findByTypeAndAvailableTrue(Vehicle.VehicleType.TUKTUK),
                startDate, endDate);
        return vehicles.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ── Get Airport Transfers ──────────────────────────────────
    public List<LocalVehicleResponse> getAirportTransfers() {
        return getAirportTransfers(null, null);
    }

    public List<LocalVehicleResponse> getAirportTransfers(LocalDate startDate, LocalDate endDate) {
        List<Vehicle> vehicles = filterByAvailability(
                vehicleRepository.findByAirportTransferTrueAndAvailableTrue(),
                startDate, endDate);
        return vehicles.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // Excludes vehicles with an active (PENDING_PAYMENT/CONFIRMED) booking
    // overlapping [start, end]. No-op when either date is missing.
    private List<Vehicle> filterByAvailability(List<Vehicle> vehicles, LocalDate start, LocalDate end) {
        if (start == null || end == null) return vehicles;
        List<Long> bookedIds = bookingRepository.findBookedVehicleIds(start, end);
        return vehicles.stream()
                .filter(v -> !bookedIds.contains(v.getId()))
                .collect(Collectors.toList());
    }

    // ── Add Vehicle (Admin) ────────────────────────────────────
    public LocalVehicleResponse addVehicle(Vehicle vehicle) {
        Vehicle saved = vehicleRepository.save(vehicle);
        log.info("Added new vehicle: {}", saved.getName());
        return toResponse(saved);
    }

    // ── Update Vehicle (Admin) ─────────────────────────────────
    public LocalVehicleResponse updateVehicle(Long id, Vehicle updatedVehicle) {
        Vehicle existingVehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Vehicle not found"));

        if (updatedVehicle.getName() != null) {
            existingVehicle.setName(updatedVehicle.getName());
        }
        if (updatedVehicle.getType() != null) {
            existingVehicle.setType(updatedVehicle.getType());
        }
        if (updatedVehicle.getCategory() != null) {
            existingVehicle.setCategory(updatedVehicle.getCategory());
        }
        existingVehicle.setBrand(updatedVehicle.getBrand());
        existingVehicle.setModel(updatedVehicle.getModel());
        existingVehicle.setYear(updatedVehicle.getYear());
        existingVehicle.setSeats(updatedVehicle.getSeats());
        existingVehicle.setColor(updatedVehicle.getColor());
        existingVehicle.setLicensePlate(updatedVehicle.getLicensePlate());
        if (updatedVehicle.getPricePerDay() != null) {
            existingVehicle.setPricePerDay(updatedVehicle.getPricePerDay());
        }
        existingVehicle.setCurrency(updatedVehicle.getCurrency());
        existingVehicle.setDistrict(updatedVehicle.getDistrict());
        existingVehicle.setPickupLocation(updatedVehicle.getPickupLocation());
        existingVehicle.setLatitude(updatedVehicle.getLatitude());
        existingVehicle.setLongitude(updatedVehicle.getLongitude());
        existingVehicle.setDriverName(updatedVehicle.getDriverName());
        existingVehicle.setDriverPhone(updatedVehicle.getDriverPhone());
        existingVehicle.setWhatsappNumber(updatedVehicle.getWhatsappNumber());
        existingVehicle.setDriverLanguages(updatedVehicle.getDriverLanguages());
        existingVehicle.setDriverIncluded(updatedVehicle.getDriverIncluded());
        existingVehicle.setAirportTransfer(updatedVehicle.getAirportTransfer());
        existingVehicle.setAvailable(updatedVehicle.getAvailable());
        existingVehicle.setDescription(updatedVehicle.getDescription());
        existingVehicle.setImageUrls(updatedVehicle.getImageUrls());

        Vehicle saved = vehicleRepository.save(existingVehicle);
        log.info("Updated vehicle: {}", saved.getName());
        return toResponse(saved);
    }

    // ── Delete Vehicle (Admin) ─────────────────────────────────
    public void deleteVehicle(Long id) {
        if (!vehicleRepository.existsById(id)) {
            throw new RuntimeException("Vehicle not found");
        }
        vehicleRepository.deleteById(id);
        log.info("Deleted vehicle with id: {}", id);
    }

    // ── Update Availability ────────────────────────────────────
    public void updateAvailability(Long id, Boolean available) {
        Vehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Vehicle not found"));
        vehicle.setAvailable(available);
        vehicleRepository.save(vehicle);
    }

    // ── Write Review ───────────────────────────────────────────
    public VehicleReviewResponse writeReview(Long vehicleId, CreateVehicleReviewRequest req) {
        User user = getCurrentUser();
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new RuntimeException("Vehicle not found: " + vehicleId));

        VehicleBooking booking = null;
        if (req.getBookingId() != null) {
            booking = bookingRepository.findById(req.getBookingId()).orElse(null);
        }

        VehicleReview review = VehicleReview.builder()
                .vehicle(vehicle)
                .user(user)
                .booking(booking)
                .rating(req.getRating())
                .comment(req.getComment())
                .build();

        reviewRepository.save(review);

        // Update vehicle average rating
        Double avgRating = reviewRepository.findAverageRatingByVehicleId(vehicleId);
        Long count = reviewRepository.countByVehicleId(vehicleId);
        vehicle.setRating(avgRating != null ? Math.round(avgRating * 10.0) / 10.0 : 0.0);
        vehicle.setReviewCount(count.intValue());
        vehicleRepository.save(vehicle);

        log.info("Review added for vehicle: {}", vehicle.getName());
        return toReviewResponse(review, user);
    }

    // ── Get Vehicle Reviews ──────────────────────────────────────
    public List<VehicleReviewResponse> getVehicleReviews(Long vehicleId) {
        return reviewRepository
                .findByVehicleIdOrderByCreatedAtDesc(vehicleId)
                .stream()
                .map(r -> toReviewResponse(r, r.getUser()))
                .collect(Collectors.toList());
    }

    private VehicleReviewResponse toReviewResponse(VehicleReview r, User user) {
        VehicleReviewResponse res = new VehicleReviewResponse();
        res.setId(r.getId());
        res.setVehicleId(r.getVehicle().getId());
        res.setUserId(user.getId());
        res.setUserName(user.getName());
        res.setRating(r.getRating());
        res.setComment(r.getComment());
        res.setCreatedAt(r.getCreatedAt());
        return res;
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
        res.setYear(v.getYear());
        res.setColor(v.getColor());
        res.setLicensePlate(v.getLicensePlate());
        res.setLatitude(v.getLatitude());
        res.setLongitude(v.getLongitude());
        res.setSeats(v.getSeats());
        res.setPricePerDay(v.getPricePerDay());
        res.setCurrency(v.getCurrency());
        res.setDistrict(v.getDistrict());
        res.setPickupLocation(v.getPickupLocation());
        res.setDriverIncluded(v.getDriverIncluded());
        res.setAirportTransfer(v.getAirportTransfer());
        res.setDriverName(v.getDriverName());
        res.setDriverPhone(v.getDriverPhone());
        res.setWhatsappNumber(v.getWhatsappNumber());
        res.setDriverLanguages(v.getDriverLanguages());
        res.setDescription(v.getDescription());
        res.setImageUrls(v.getImageUrls());
        res.setRating(v.getRating());
        res.setReviewCount(v.getReviewCount());
        res.setAvailable(v.getAvailable());
        return res;
    }
}