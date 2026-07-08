package com.exploreceylon.backend.repository;

import com.exploreceylon.backend.model.GuideBooking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface GuideBookingRepository
        extends JpaRepository<GuideBooking, Long> {

    List<GuideBooking> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<GuideBooking> findByGuideIdOrderByCreatedAtDesc(Long guideId);
    List<GuideBooking> findByTripIdOrderByStartDate(Long tripId);

    // Availability — only active bookings (PENDING_PAYMENT/CONFIRMED) block dates
    @Query("SELECT COUNT(b) FROM GuideBooking b WHERE " +
           "b.guide.id = :guideId AND " +
           "b.status IN (com.exploreceylon.backend.model.GuideBooking.BookingStatus.PENDING_PAYMENT, " +
           "             com.exploreceylon.backend.model.GuideBooking.BookingStatus.CONFIRMED) AND " +
           "b.startDate <= :end AND b.endDate >= :start")
    Long countOverlappingBookings(
            @Param("guideId") Long guideId,
            @Param("start")   LocalDate start,
            @Param("end")     LocalDate end);

    // Listing filter — every guide with an active booking overlapping the range
    @Query("SELECT DISTINCT b.guide.id FROM GuideBooking b WHERE " +
           "b.status IN (com.exploreceylon.backend.model.GuideBooking.BookingStatus.PENDING_PAYMENT, " +
           "             com.exploreceylon.backend.model.GuideBooking.BookingStatus.CONFIRMED) AND " +
           "b.startDate <= :end AND b.endDate >= :start")
    List<Long> findBookedGuideIds(
            @Param("start") LocalDate start,
            @Param("end")   LocalDate end);

    // Scheduled cleanup — expired unpaid bookings
    List<GuideBooking> findByStatusAndCreatedAtBefore(
            GuideBooking.BookingStatus status, LocalDateTime cutoff);

    // Balance reminders — confirmed bookings ending on a given date
    List<GuideBooking> findByStatusAndEndDate(
            GuideBooking.BookingStatus status, LocalDate endDate);
}