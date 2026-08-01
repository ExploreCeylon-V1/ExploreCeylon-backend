package com.exploreceylon.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "hotel_bookings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotelBooking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── Relationships ─────────────────────────────────────────
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_day_id")
    private TripDay tripDay;                 // nullable: booking can exist before day assignment

    // ── Hotel data (snapshot from Booking.com API) ────────────
    // We store a snapshot so if the API changes, old bookings stay intact
    @Column(nullable = false)
    private String hotelApiId;               // hotel_id from Booking.com

    @Column(nullable = false)
    private String hotelName;

    private String hotelAddress;

    private String photoUrl;

    @Builder.Default
    private Integer stars = 0;

    @Builder.Default
    private Double reviewScore = 0.0;

    private String reviewScoreWord;

    // ── Booking dates ─────────────────────────────────────────
    @Column(nullable = false)
    private LocalDate checkIn;

    @Column(nullable = false)
    private LocalDate checkOut;

    @Builder.Default
    private Integer adults = 1;

    @Builder.Default
    private Integer rooms = 1;

    // ── Pricing ───────────────────────────────────────────────
    @Column(nullable = false)
    private Double totalCost;               // pricePerNight × nights

    @Builder.Default
    private String currency = "USD";

    private Double pricePerNight;

    // ── Status ────────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private BookingStatus status = BookingStatus.CONFIRMED;

    // ── Audit ─────────────────────────────────────────────────
    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum BookingStatus {
        CONFIRMED,   // saved by user
        CANCELLED    // removed by user
    }
}