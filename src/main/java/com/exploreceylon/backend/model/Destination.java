package com.exploreceylon.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "destinations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Destination {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String district;

    @Column(nullable = false)
    private String province;

    @Column(length = 3000)
    private String description;

    @Column(length = 1000)
    private String shortDescription;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DestinationCategory category;

    // Comma separated: "January,February,March"
    private String bestMonths;

    // Comma separated: "hiking,swimming,wildlife"
    private String activities;

    private Double latitude;
    private Double longitude;

    private String coverImageUrl;

    @ElementCollection
    @CollectionTable(
        name = "destination_images",
        joinColumns = @JoinColumn(name = "destination_id")
    )
    @Column(name = "image_url")
    @Builder.Default
    private List<String> imageUrls = new ArrayList<>();

    // Comma separated: "Colombo:3h by road,Kandy:2h by road"
    private String travelTimeFrom;

    private String entryFee;
    private String openingHours;

    @Builder.Default
    private Boolean featured = false;

    @Builder.Default
    private Boolean active = true;

    @Builder.Default
    private Double rating = 0.0;

    @Builder.Default
    private Integer reviewCount = 0;

    private String unescoStatus;
    private String nearbyGems;

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

    public enum DestinationCategory {
        BEACH, CULTURAL, WILDLIFE, HILL,
        SURF, ADVENTURE, HERITAGE, RELIGIOUS, CITY
    }
}