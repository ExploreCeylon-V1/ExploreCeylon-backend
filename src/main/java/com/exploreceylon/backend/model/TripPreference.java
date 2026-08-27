package com.exploreceylon.backend.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "trip_preferences")
@Getter
@Setter
@ToString(exclude = "trip")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "to_location")
    private String toLocation;

    @Column(name = "from_location")
    private String fromLocation;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false, unique = true)
    private Trip trip;

    @Column(length = 500)
    private String regions;

    @Column(length = 500)
    private String interests;

    private String startingPoint;

    @Column(length = 1000)
    private String specialNotes;
}