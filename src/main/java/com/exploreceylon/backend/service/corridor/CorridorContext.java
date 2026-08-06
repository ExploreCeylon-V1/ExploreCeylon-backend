package com.exploreceylon.backend.service.corridor;

import com.exploreceylon.backend.service.ItineraryAssemblyService.GeoPoint;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Context parameters passed to TravelCorridorEngine for filtering candidate destinations.
 */
@Data
@Builder
public class CorridorContext {
    private GeoPoint origin;
    private GeoPoint destination;
    private String encodedPolyline;
    private List<GeoPoint> routePath;
    private double widthKm;
    private double maxDetourKm;
    private boolean corridorEnabled;
}
