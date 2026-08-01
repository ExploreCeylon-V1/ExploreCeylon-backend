package com.exploreceylon.backend.repository;

import com.exploreceylon.backend.model.HotelBooking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HotelBookingRepository extends JpaRepository<HotelBooking, Long> {

    // All bookings for a trip (for trip planner view)
    List<HotelBooking> findByTripIdOrderByCheckIn(Long tripId);

    // All bookings for a specific day
    List<HotelBooking> findByTripDayId(Long tripDayId);

    // Check if this hotel is already booked on this trip day
    boolean existsByTripDayIdAndHotelApiId(Long tripDayId, String hotelApiId);

    // For admin — all bookings
    List<HotelBooking> findAllByOrderByCreatedAtDesc();

    // Find by status
    List<HotelBooking> findByTripIdAndStatus(Long tripId, HotelBooking.BookingStatus status);

    // Cancel — find by id and trip (security: user can only cancel their own)
    Optional<HotelBooking> findByIdAndTripId(Long id, Long tripId);
}