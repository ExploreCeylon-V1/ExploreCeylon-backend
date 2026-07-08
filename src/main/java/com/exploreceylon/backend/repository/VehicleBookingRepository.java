package com.exploreceylon.backend.repository;

import com.exploreceylon.backend.model.VehicleBooking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface VehicleBookingRepository
        extends JpaRepository<VehicleBooking, Long> {

    long countByStatus(VehicleBooking.BookingStatus status);

    @Query("SELECT COALESCE(SUM(b.totalCost), 0) FROM VehicleBooking b " +
           "WHERE CAST(b.status AS string) = :status")
    Double sumRevenueByStatus(@Param("status") String status);   

    // My bookings
    List<VehicleBooking> findByUserIdOrderByCreatedAtDesc(Long userId);

    // Trip bookings
    List<VehicleBooking> findByTripIdOrderByPickupDate(Long tripId);

    // Vehicle bookings
    List<VehicleBooking> findByVehicleIdOrderByPickupDate(Long vehicleId);

    // Check availability — only active bookings (PENDING_PAYMENT/CONFIRMED) block dates
    @Query("SELECT COUNT(b) FROM VehicleBooking b WHERE " +
           "b.vehicle.id = :vehicleId AND " +
           "b.status IN (com.exploreceylon.backend.model.VehicleBooking.BookingStatus.PENDING_PAYMENT, " +
           "             com.exploreceylon.backend.model.VehicleBooking.BookingStatus.CONFIRMED) AND " +
           "b.pickupDate <= :dropoff AND " +
           "b.dropoffDate >= :pickup")
    Long countOverlappingBookings(
            @Param("vehicleId")  Long vehicleId,
            @Param("pickup")     LocalDate pickup,
            @Param("dropoff")    LocalDate dropoff);

    // Listing filter — every vehicle with an active booking overlapping the range
    @Query("SELECT DISTINCT b.vehicle.id FROM VehicleBooking b WHERE " +
           "b.status IN (com.exploreceylon.backend.model.VehicleBooking.BookingStatus.PENDING_PAYMENT, " +
           "             com.exploreceylon.backend.model.VehicleBooking.BookingStatus.CONFIRMED) AND " +
           "b.pickupDate <= :dropoff AND " +
           "b.dropoffDate >= :pickup")
    List<Long> findBookedVehicleIds(
            @Param("pickup")  LocalDate pickup,
            @Param("dropoff") LocalDate dropoff);

    // Scheduled cleanup — expired unpaid bookings
    List<VehicleBooking> findByStatusAndCreatedAtBefore(
            VehicleBooking.BookingStatus status, LocalDateTime cutoff);

    // Balance reminders — confirmed bookings ending on a given date
    List<VehicleBooking> findByStatusAndDropoffDate(
            VehicleBooking.BookingStatus status, LocalDate dropoffDate);
}