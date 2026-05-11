package com.exploreceylon.backend.repository;

import com.exploreceylon.backend.model.VehicleBooking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface VehicleBookingRepository
        extends JpaRepository<VehicleBooking, Long> {

    // My bookings
    List<VehicleBooking> findByUserIdOrderByCreatedAtDesc(Long userId);

    // Trip bookings
    List<VehicleBooking> findByTripIdOrderByPickupDate(Long tripId);

    // Vehicle bookings
    List<VehicleBooking> findByVehicleIdOrderByPickupDate(Long vehicleId);

    // Check availability — overlapping dates
    @Query("SELECT COUNT(b) FROM VehicleBooking b WHERE " +
           "b.vehicle.id = :vehicleId AND " +
           "b.status != 'CANCELLED' AND " +
           "b.pickupDate <= :dropoff AND " +
           "b.dropoffDate >= :pickup")
    Long countOverlappingBookings(
            @Param("vehicleId")  Long vehicleId,
            @Param("pickup")     LocalDate pickup,
            @Param("dropoff")    LocalDate dropoff);
}