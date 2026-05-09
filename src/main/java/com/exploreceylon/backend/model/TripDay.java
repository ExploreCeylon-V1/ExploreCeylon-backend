package com.exploreceylon.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "trip_days")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @Column(nullable = false)
    private Integer dayNumber;

    @Column(nullable = false)
    private LocalDate date;

    private String region;
    private String theme;

    @Column(length = 1000)
    private String tips;

    private Long festivalEventId;

    @Builder.Default
    private Double estimatedDayCost = 0.0;

    @OneToMany(mappedBy = "tripDay",
               cascade = CascadeType.ALL,
               orphanRemoval = true)
    @Builder.Default
    private List<TripDayItem> items = new ArrayList<>();
}