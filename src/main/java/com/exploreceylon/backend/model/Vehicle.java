package com.exploreceylon.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "vehicles")
@Data
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VehicleType type;

    private String brand;
    private String model;
    private Integer year;
    private Integer seats;
    private String color;
    private String licensePlate;

    @Column(nullable = false)
    private Double pricePerDay;

    private String currency = "USD";

    @Column(nullable = false)
    private String district;

    private String pickupLocation;
    private Double latitude;
    private Double longitude;

    private String driverName;
    private String driverPhone;
    // Raw WhatsApp number, no '+' or spaces, e.g. "94771234567"
    private String whatsappNumber;
    private String driverLanguages;

    private Boolean driverIncluded = false;
    private Boolean airportTransfer = false;
    private Boolean available = true;

    @Column(length = 1000)
    private String description;

    @ElementCollection
    @CollectionTable(
        name = "vehicle_images",
        joinColumns = @JoinColumn(name = "vehicle_id")
    )
    @Column(name = "image_url")
    private List<String> imageUrls;

    private Double rating = 0.0;
    private Integer reviewCount = 0;

    @Enumerated(EnumType.STRING)
    private VehicleCategory category;

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

    // ── Enums ──────────────────────────────────────────────────
    public enum VehicleType {
        CAR, VAN, SUV, TUKTUK, SCOOTER, BUS, MINIVAN
    }

    public enum VehicleCategory {
        ECONOMY, STANDARD, PREMIUM, LUXURY, LOCAL
    }
}