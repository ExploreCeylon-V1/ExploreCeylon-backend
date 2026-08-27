package com.exploreceylon.backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "vehicle_bookings")
@Getter
@Setter
@ToString(exclude = {"vehicle", "user", "trip"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleBooking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id")
    private Trip trip;

    @Column(nullable = false)
    private LocalDate pickupDate;

    @Column(nullable = false)
    private LocalDate dropoffDate;

    private String pickupTime;
    private String dropoffTime;

    @Column(nullable = false)
    private String pickupLocation;

    private String dropoffLocation;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private BookingStatus status = BookingStatus.PENDING_PAYMENT;

    @Column(nullable = false)
    private Double totalCost;

    // 20% advance / 80% balance — persisted at creation (single source of truth)
    private Double advanceAmount;
    private Double balanceAmount;

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