package com.exploreceylon.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "trips")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Trip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "title", nullable = true, length = 200)
    private String title;

    @Column(name = "from_location")
    private String fromLocation;      // "Heading from"

    @Column(name = "to_location")
    private String toLocation;        // "Where to?"

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    private TravelStyle travelStyle;

    @Enumerated(EnumType.STRING)
    private BudgetRange budgetRange;

    @Builder.Default
    private Integer groupSize = 1;

    // Optional numeric total-trip budget in LKR, set by the user. Nullable
    // — used only to validate the generated itinerary's estimated cost
    // and surface a warning note; never used to auto-swap destinations.
    private Double budgetAmountLkr;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private TripStatus status = TripStatus.DRAFT;

    @Builder.Default
    private Boolean aiGenerated = false;

    @Column(unique = true)
    private String shareToken;

    @OneToMany(mappedBy = "trip",
               cascade = CascadeType.ALL,
               orphanRemoval = true)
    @Builder.Default
    private List<TripDay> days = new ArrayList<>();

    @OneToOne(mappedBy = "trip",
              cascade = CascadeType.ALL,
              orphanRemoval = true)
    private TripPreference preference;

    @Column(updatable = false)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (shareToken == null) {
            shareToken = java.util.UUID.randomUUID().toString();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ── Enums ──────────────────────────────────────────────
    public enum TravelStyle {
        ADVENTURE, CULTURAL, RELAXATION,
        FAMILY, HONEYMOON, PILGRIMAGE, WILDLIFE, PHOTOGRAPHY
    }

    public enum BudgetRange {
        BUDGET, MID_RANGE, LUXURY
    }

    public enum TripStatus {
        DRAFT, CONFIRMED, COMPLETED, CANCELLED, PLANNING, GENERATED, ACTIVE, ARCHIVED
    }
}