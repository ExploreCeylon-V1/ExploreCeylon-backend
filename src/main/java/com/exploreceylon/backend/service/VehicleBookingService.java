package com.exploreceylon.backend.service;

import com.exploreceylon.backend.dto.Vehicle.BookVehicleRequest;
import com.exploreceylon.backend.dto.Vehicle.VehicleBookingResponse;
import com.exploreceylon.backend.model.*;
import com.exploreceylon.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class VehicleBookingService {

    private final VehicleBookingRepository bookingRepository;
    private final VehicleRepository        vehicleRepository;
    private final UserRepository           userRepository;
    private final TripRepository           tripRepository;

    // ── Current User ───────────────────────────────────────
    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext()
                .getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException(
                        "User not found"));
    }

    // ── Book Vehicle ───────────────────────────────────────
    public VehicleBookingResponse bookVehicle(BookVehicleRequest req) {
        User user = getCurrentUser();

        // Get vehicle
        Vehicle vehicle = vehicleRepository.findById(req.getVehicleId())
                .orElseThrow(() -> new RuntimeException(
                        "Vehicle not found: " + req.getVehicleId()));

        // Check availability
        Long overlapping = bookingRepository.countOverlappingBookings(
                req.getVehicleId(),
                req.getPickupDate(),
                req.getDropoffDate());

        if (overlapping > 0) {
            throw new RuntimeException(
                    "Vehicle not available for selected dates");
        }

        // Calculate total cost
        long days = ChronoUnit.DAYS.between(
                req.getPickupDate(), req.getDropoffDate()) + 1;
        double totalCost = vehicle.getPricePerDay() * days;

        // Get trip if provided
        Trip trip = null;
        if (req.getTripId() != null) {
            trip = tripRepository.findById(req.getTripId())
                    .orElse(null);
        }

        VehicleBooking booking = VehicleBooking.builder()
                .vehicle(vehicle)
                .user(user)
                .trip(trip)
                .pickupDate(req.getPickupDate())
                .dropoffDate(req.getDropoffDate())
                .pickupTime(req.getPickupTime())
                .dropoffTime(req.getDropoffTime())
                .pickupLocation(req.getPickupLocation())
                .dropoffLocation(req.getDropoffLocation() != null
                        ? req.getDropoffLocation()
                        : req.getPickupLocation())
                .totalCost(totalCost)
                .notes(req.getNotes())
                .status(VehicleBooking.BookingStatus.CONFIRMED)
                .build();

        VehicleBooking saved = bookingRepository.save(booking);
        log.info("Vehicle booked: {} by {} — {} days — ${}",
                vehicle.getName(), user.getEmail(), days, totalCost);
        return toResponse(saved);
    }

    // ── Get My Bookings ────────────────────────────────────
    public List<VehicleBookingResponse> getMyBookings() {
        User user = getCurrentUser();
        return bookingRepository
                .findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ── Get Booking By ID ──────────────────────────────────
    public VehicleBookingResponse getBookingById(Long id) {
        VehicleBooking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException(
                        "Booking not found: " + id));
        return toResponse(booking);
    }

    // ── Get Trip Vehicle Bookings ──────────────────────────
    public List<VehicleBookingResponse> getTripBookings(Long tripId) {
        return bookingRepository
                .findByTripIdOrderByPickupDate(tripId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ── Cancel Booking ─────────────────────────────────────
    public VehicleBookingResponse cancelBooking(Long id) {
        VehicleBooking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException(
                        "Booking not found: " + id));
        booking.setStatus(VehicleBooking.BookingStatus.CANCELLED);
        log.info("Vehicle booking cancelled: {}", id);
        return toResponse(bookingRepository.save(booking));
    }

    // ── Admin — Update Status ──────────────────────────────
    public VehicleBookingResponse updateStatus(
            Long id, String status) {
        VehicleBooking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException(
                        "Booking not found: " + id));
        booking.setStatus(
                VehicleBooking.BookingStatus.valueOf(
                        status.toUpperCase()));
        return toResponse(bookingRepository.save(booking));
    }

    // ── Check Availability ─────────────────────────────────
    public boolean checkAvailability(Long vehicleId,
                                     java.time.LocalDate pickup,
                                     java.time.LocalDate dropoff) {
        Long overlapping = bookingRepository
                .countOverlappingBookings(vehicleId, pickup, dropoff);
        return overlapping == 0;
    }

    // ── MAPPER ─────────────────────────────────────────────
    private VehicleBookingResponse toResponse(VehicleBooking b) {
        VehicleBookingResponse res = new VehicleBookingResponse();
        res.setId(b.getId());
        res.setVehicleId(b.getVehicle().getId());
        res.setVehicleName(b.getVehicle().getName());
        res.setVehicleType(b.getVehicle().getType().name());
        res.setDriverName(b.getVehicle().getDriverName());
        res.setUserId(b.getUser().getId());
        res.setTripId(b.getTrip() != null
                ? b.getTrip().getId() : null);
        res.setPickupDate(b.getPickupDate());
        res.setDropoffDate(b.getDropoffDate());
        res.setPickupTime(b.getPickupTime());
        res.setDropoffTime(b.getDropoffTime());
        res.setPickupLocation(b.getPickupLocation());
        res.setDropoffLocation(b.getDropoffLocation());
        res.setStatus(b.getStatus());
        res.setTotalCost(b.getTotalCost());
        res.setNotes(b.getNotes());
        res.setCreatedAt(b.getCreatedAt());
        return res;
    }
}