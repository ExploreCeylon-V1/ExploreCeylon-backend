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
@Table(name = "tour_guides")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TourGuide {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fullName;

    @Column(length = 2000)
    private String bio;

    private String photoUrl;

    // Comma separated: "English,Sinhala,Tamil"
    @Column(nullable = false)
    private String languages;

    // Comma separated: "CULTURAL,WILDLIFE"
    @Column(nullable = false)
    private String specialties;

    @Column(nullable = false)
    private String district;

    @Column(nullable = false)
    private Double pricePerDay;

    @Builder.Default
    private Boolean verified = false;

    @Builder.Default
    private Boolean available = true;

    @Builder.Default
    private Double rating = 0.0;

    @Builder.Default
    private Integer reviewCount = 0;

    private String phone;
    // Raw WhatsApp number, no '+' or spaces, e.g. "94771234567"
    private String whatsappNumber;
    private String email;

    @ElementCollection
    @CollectionTable(
        name = "guide_images",
        joinColumns = @JoinColumn(name = "guide_id")
    )
    @Column(name = "image_url")
    @Builder.Default
    private List<String> imageUrls = new ArrayList<>();

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
}
