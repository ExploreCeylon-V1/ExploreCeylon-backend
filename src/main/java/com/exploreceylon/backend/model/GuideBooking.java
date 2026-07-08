package com.exploreceylon.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "guide_bookings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuideBooking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guide_id", nullable = false)
    private TourGuide guide;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id")
    private Trip trip;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private BookingStatus status = BookingStatus.PENDING_PAYMENT;

    @Column(nullable = false)
    private Double totalCost;

    // 20% advance / 80% balance — persisted at creation (single source of truth)
    private Double advanceAmount;
    private Double balanceAmount;

    @Builder.Default
    private Double commission = 0.0;

    @Column(length = 500)
    private String notes;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum BookingStatus {
        PENDING_PAYMENT, // booking created, awaiting 20% advance
        CONFIRMED,       // 20% advance paid via PayHere
        COMPLETED,       // 80% balance paid via PayHere — fully settled
        CANCELLED        // booking cancelled / expired
    }
}