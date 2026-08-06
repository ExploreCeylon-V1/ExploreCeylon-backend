package com.exploreceylon.backend.service.progression;

import com.exploreceylon.backend.service.ItineraryAssemblyService.GeoPoint;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Context parameters passed to JourneyProgressionEngine.
 */
@Data
@Builder
public class ProgressionContext {
    private GeoPoint origin;
    private GeoPoint destination;
    private String encodedPolyline;
    private List<GeoPoint> routePath;
    private double minimumForwardDistanceKm;
    private boolean allowBacktracking;
    private boolean progressionEnabled;
}
