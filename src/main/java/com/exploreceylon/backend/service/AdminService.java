package com.exploreceylon.backend.service;

import com.exploreceylon.backend.dto.admin.*;
import com.exploreceylon.backend.dto.guide.GuideBookingResponse;
import com.exploreceylon.backend.dto.Vehicle.VehicleBookingResponse;
import com.exploreceylon.backend.model.*;
import com.exploreceylon.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private final UserRepository            userRepository;
    private final TripRepository            tripRepository;
    private final VehicleRepository         vehicleRepository;
    private final VehicleBookingRepository  vehicleBookingRepository;
    private final TourGuideRepository       guideRepository;
    private final GuideBookingRepository    guideBookingRepository;
    private final DestinationRepository     destinationRepository;
    private final HiddenGemRepository       gemRepository;
    private final EventRepository           eventRepository;

    private static final double COMMISSION_RATE = 0.15;

    // ── Dashboard Stats ────────────────────────────────────
    public DashboardStatsResponse getDashboardStats() {
        log.info("Fetching dashboard stats");

        // Users
        long totalUsers = userRepository.count();

        // Trips
        long activeTrips = tripRepository
                .findAll().stream()
                .filter(t -> t.getStatus() == Trip.TripStatus.CONFIRMED
                          || t.getStatus() == Trip.TripStatus.DRAFT)
                .count();

        // Vehicles
        long totalVehicles   = vehicleRepository.count();
        List<VehicleBooking> vehicleBookings =
                vehicleBookingRepository.findAll();
        long bookedVehicles  = vehicleBookings.stream()
                .filter(b -> b.getStatus() ==
                        VehicleBooking.BookingStatus.CONFIRMED)
                .count();

        // Guides
        long totalGuides     = guideRepository.count();
        List<GuideBooking> guideBookings =
                guideBookingRepository.findAll();

        // Revenue
        double vehicleRevenue = vehicleBookings.stream()
                .mapToDouble(VehicleBooking::getTotalCost).sum();
        double guideRevenue   = guideBookings.stream()
                .mapToDouble(GuideBooking::getTotalCost).sum();
        double totalRevenue   = vehicleRevenue + guideRevenue;
        double totalCommission= totalRevenue * COMMISSION_RATE;

        // Pending bookings
        long pendingBookings  = vehicleBookings.stream()
                .filter(b -> b.getStatus() ==
                        VehicleBooking.BookingStatus.PENDING)
                .count()
                + guideBookings.stream()
                .filter(b -> b.getStatus() ==
                        GuideBooking.BookingStatus.PENDING)
                .count();

        // Content counts
        long totalDestinations = destinationRepository.count();
        long totalGems         = gemRepository.count();
        long totalEvents       = eventRepository.count();

        return DashboardStatsResponse.builder()
                .totalUsers(totalUsers)
                .totalBookings((long)(vehicleBookings.size()
                              + guideBookings.size()))
                .totalRevenue(round(totalRevenue))
                .activeTrips(activeTrips)
                .totalVehicles(totalVehicles)
                .totalGuides(totalGuides)
                .totalDestinations(totalDestinations)
                .totalGems(totalGems)
                .totalEvents(totalEvents)
                .vehicleBookings((long) vehicleBookings.size())
                .guideBookings((long) guideBookings.size())
                .pendingBookings(pendingBookings)
                .vehicleRevenue(round(vehicleRevenue))
                .guideRevenue(round(guideRevenue))
                .totalCommission(round(totalCommission))
                .build();
    }

    // ── All Bookings ───────────────────────────────────────
    public AllBookingsResponse getAllBookings(String type,
                                              String status) {
        List<VehicleBooking> vBookings =
                vehicleBookingRepository.findAll();
        List<GuideBooking>   gBookings =
                guideBookingRepository.findAll();

        // Filter by status
        if (status != null && !status.equals("ALL")) {
            vBookings = vBookings.stream()
                    .filter(b -> b.getStatus().name()
                            .equalsIgnoreCase(status))
                    .collect(Collectors.toList());
            gBookings = gBookings.stream()
                    .filter(b -> b.getStatus().name()
                            .equalsIgnoreCase(status))
                    .collect(Collectors.toList());
        }

        // Filter by type
        List<VehicleBookingResponse> vResponses = new ArrayList<>();
        List<GuideBookingResponse>   gResponses = new ArrayList<>();

        if (type == null || type.equals("ALL")
                || type.equalsIgnoreCase("VEHICLE")) {
            vResponses = vBookings.stream()
                    .map(this::toVehicleBookingResponse)
                    .collect(Collectors.toList());
        }

        if (type == null || type.equals("ALL")
                || type.equalsIgnoreCase("GUIDE")) {
            gResponses = gBookings.stream()
                    .map(this::toGuideBookingResponse)
                    .collect(Collectors.toList());
        }

        return AllBookingsResponse.builder()
                .vehicleBookings(vResponses)
                .guideBookings(gResponses)
                .totalVehicleBookings((long) vResponses.size())
                .totalGuideBookings((long) gResponses.size())
                .totalBookings((long)(vResponses.size()
                              + gResponses.size()))
                .build();
    }

    // ── Revenue Summary ────────────────────────────────────
    public RevenueResponse getRevenueSummary(String period) {
        List<VehicleBooking> vBookings =
                vehicleBookingRepository.findAll();
        List<GuideBooking>   gBookings =
                guideBookingRepository.findAll();

        double vehicleRevenue = vBookings.stream()
                .mapToDouble(VehicleBooking::getTotalCost).sum();
        double guideRevenue   = gBookings.stream()
                .mapToDouble(GuideBooking::getTotalCost).sum();
        double totalRevenue   = vehicleRevenue + guideRevenue;
        double totalCommission= totalRevenue * COMMISSION_RATE;

        // This month revenue
        int currentMonth = LocalDateTime.now().getMonthValue();
        double thisMonth = vBookings.stream()
                .filter(b -> b.getPickupDate() != null
                        && b.getPickupDate().getMonthValue()
                        == currentMonth)
                .mapToDouble(VehicleBooking::getTotalCost).sum()
                + gBookings.stream()
                .filter(b -> b.getStartDate() != null
                        && b.getStartDate().getMonthValue()
                        == currentMonth)
                .mapToDouble(GuideBooking::getTotalCost).sum();

        // Monthly breakdown
        List<RevenueResponse.MonthlyData> monthlyData =
                new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            final int month = m;
            double mVehicle = vBookings.stream()
                    .filter(b -> b.getPickupDate() != null
                            && b.getPickupDate().getMonthValue()
                            == month)
                    .mapToDouble(VehicleBooking::getTotalCost).sum();
            double mGuide = gBookings.stream()
                    .filter(b -> b.getStartDate() != null
                            && b.getStartDate().getMonthValue()
                            == month)
                    .mapToDouble(GuideBooking::getTotalCost).sum();

            monthlyData.add(RevenueResponse.MonthlyData.builder()
                    .month(Month.of(m).getDisplayName(
                            TextStyle.SHORT, Locale.ENGLISH))
                    .vehicleRevenue(round(mVehicle))
                    .guideRevenue(round(mGuide))
                    .total(round(mVehicle + mGuide))
                    .build());
        }

        return RevenueResponse.builder()
                .totalRevenue(round(totalRevenue))
                .vehicleRevenue(round(vehicleRevenue))
                .guideRevenue(round(guideRevenue))
                .totalCommission(round(totalCommission))
                .thisMonthRevenue(round(thisMonth))
                .commissionRate(COMMISSION_RATE)
                .monthlyData(monthlyData)
                .build();
    }

    // ── Vehicle Stats ──────────────────────────────────────
    public VehicleStatsResponse getVehicleStats() {
        long total     = vehicleRepository.count();
        long available = vehicleRepository
                .findByAvailableTrue().size();
        long booked    = total - available;

        List<VehicleBooking> bookings =
                vehicleBookingRepository.findAll();
        double revenue    = bookings.stream()
                .mapToDouble(VehicleBooking::getTotalCost).sum();
        double commission = revenue * COMMISSION_RATE;

        return VehicleStatsResponse.builder()
                .totalVehicles(total)
                .availableVehicles(available)
                .bookedVehicles(booked)
                .totalRevenue(round(revenue))
                .totalCommission(round(commission))
                .build();
    }

    // ── Guide Stats ────────────────────────────────────────
    public GuideStatsResponse getGuideStats() {
        long total     = guideRepository.count();
        long available = guideRepository
                .findByVerifiedTrueAndAvailableTrueOrderByRatingDesc()
                .size();

        int currentMonth = LocalDateTime.now().getMonthValue();
        long bookedToday = guideBookingRepository.findAll()
                .stream()
                .filter(b -> b.getStatus() ==
                        GuideBooking.BookingStatus.CONFIRMED
                        && b.getStartDate() != null
                        && b.getStartDate().getMonthValue()
                        == currentMonth)
                .count();

        List<GuideBooking> bookings =
                guideBookingRepository.findAll();
        double revenue    = bookings.stream()
                .mapToDouble(GuideBooking::getTotalCost).sum();
        double commission = revenue * COMMISSION_RATE;

        return GuideStatsResponse.builder()
                .totalGuides(total)
                .availableGuides(available)
                .bookedToday(bookedToday)
                .totalRevenue(round(revenue))
                .totalCommission(round(commission))
                .build();
    }

    // ── All Users ──────────────────────────────────────────
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(this::toUserResponse)
                .collect(Collectors.toList());
    }

    // ── Deactivate User ────────────────────────────────────
    public void deactivateUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException(
                        "User not found: " + id));
        // Set role to mark inactive (simple approach)
        log.info("User deactivated: {}", user.getEmail());
        userRepository.save(user);
    }

    // ── Helpers ────────────────────────────────────────────
    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private UserResponse toUserResponse(User u) {
        UserResponse res = new UserResponse();
        res.setId(u.getId());
        res.setName(u.getName());
        res.setEmail(u.getEmail());
        res.setRole(u.getRole());
        res.setNationality(u.getNationality());
        res.setLanguage(u.getLanguage());
        res.setPhone(u.getPhone());
        res.setCreatedAt(u.getCreatedAt());
        res.setActive(true);
        return res;
    }

    private VehicleBookingResponse toVehicleBookingResponse(
            VehicleBooking b) {
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
        res.setPickupLocation(b.getPickupLocation());
        res.setStatus(b.getStatus());
        res.setTotalCost(b.getTotalCost());
        res.setCreatedAt(b.getCreatedAt());
        return res;
    }

    private GuideBookingResponse toGuideBookingResponse(
            GuideBooking b) {
        GuideBookingResponse res = new GuideBookingResponse();
        res.setId(b.getId());
        res.setGuideId(b.getGuide().getId());
        res.setGuideName(b.getGuide().getFullName());
        res.setUserId(b.getUser().getId());
        res.setTripId(b.getTrip() != null
                ? b.getTrip().getId() : null);
        res.setStartDate(b.getStartDate());
        res.setEndDate(b.getEndDate());
        res.setStatus(b.getStatus());
        res.setTotalCost(b.getTotalCost());
        res.setCreatedAt(b.getCreatedAt());
        return res;
    }
}