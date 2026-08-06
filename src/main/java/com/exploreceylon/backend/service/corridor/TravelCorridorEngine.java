package com.exploreceylon.backend.service.corridor;

import com.exploreceylon.backend.model.Destination;

import java.util.List;

/**
 * Strategy interface for filtering candidate destinations within a road route corridor.
 */
public interface TravelCorridorEngine {

    /**
     * Filters candidate destinations to retain only those falling inside the road travel corridor.
     *
     * @param candidates List of unfiltered candidate destinations.
     * @param context    Corridor context parameters (route path, width, detour limits).
     * @return Filtered list of candidate destinations falling within the travel corridor.
     */
    List<Destination> filterCandidates(List<Destination> candidates, CorridorContext context);
}
