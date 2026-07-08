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
@Table(name = "hidden_gems")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HiddenGem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(length = 3000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GemCategory category;

    @Column(nullable = false)
    private String district;

    private Double latitude;
    private Double longitude;

    @Column(length = 1000)
    private String howToGetThere;

    private String bestTime;

    @Column(length = 1000)
    private String tips;

    // Comma separated, same convention as Destination.bestMonths — months
    // this gem is best visited in. Nullable — NULL means "any season".
    private String seasonMonths;

    // Comma separated travel-style tags, same convention as
    // Destination.travelStyleTags.
    private String travelStyleTags;

    // Nullable — NULL means "fits any budget tier".
    @Enumerated(EnumType.STRING)
    private com.exploreceylon.backend.model.BudgetLevel budgetLevel;

    // Numeric entry fee in USD, nullable — see Destination.entryFeeUsd.
    private Double entryFeeUsd;

    // Typical visit duration in minutes, nullable — see
    // Destination.visitDurationMinutes.
    private Integer visitDurationMinutes;

    @ElementCollection
    @CollectionTable(
        name = "gem_images",
        joinColumns = @JoinColumn(name = "gem_id")
    )
    @Column(name = "image_url")
    @Builder.Default
    private List<String> imageUrls = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submitted_by")
    private User submittedBy;

    @Builder.Default
    private Boolean approved = false;

    @Builder.Default
    private Double rating = 0.0;

    @Builder.Default
    private Integer reviewCount = 0;

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

    // ── Enum ───────────────────────────────────────────────
    public enum GemCategory {
        BEACH, WATERFALL, RUINS,
        VIEWPOINT, VILLAGE, CAFE, TEMPLE
    }
}