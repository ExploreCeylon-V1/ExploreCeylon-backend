package com.exploreceylon.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Minimal place-name gazetteer used to resolve user-typed "from"/"to"
 * locations (cities, towns, districts) to coordinates for trip-planning
 * corridor building. Deliberately separate from {@link Destination} —
 * destinations are tourist attractions, not a general place lookup, and
 * keyword-searching them for geocoding produced false substring matches
 * (e.g. "Galle" matching "Kitulgalle"). See ItineraryAssemblyService.geocode.
 */
@Entity
@Table(name = "locations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Location {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;
}
