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

    @Column(nullable = false)
    private String title;

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
        FAMILY, HONEYMOON, PILGRIMAGE, WILDLIFE
    }

    public enum BudgetRange {
        BUDGET, MID_RANGE, LUXURY
    }

    public enum TripStatus {
        DRAFT, CONFIRMED, COMPLETED, CANCELLED
    }
}