package com.exploreceylon.backend.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "trip_day_items")
@Getter
@Setter
@ToString(exclude = "tripDay")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripDayItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_day_id", nullable = false)
    private TripDay tripDay;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ItemType type;

    private String referenceId;

    @Column(nullable = false)
    private String title;

    @Builder.Default
    private Double cost = 0.0;

    @Builder.Default
    private String currency = "USD";

    @Builder.Default
    private Boolean booked = false;

    @Builder.Default
    private Integer orderIndex = 0;

    @Column(length = 500)
    private String notes;

    // ── Enum ───────────────────────────────────────────────
    public enum ItemType {
        HOTEL, VEHICLE, GUIDE,
        ACTIVITY, GEM, FOOD, TRANSPORT
    }
}