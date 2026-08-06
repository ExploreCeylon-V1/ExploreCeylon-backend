package com.exploreceylon.backend.service.matrix;

import com.exploreceylon.backend.dto.matrix.RouteMatrix;
import com.exploreceylon.backend.dto.matrix.RouteMatrixContext;
import com.exploreceylon.backend.dto.matrix.RouteMatrixEntry;
import com.exploreceylon.backend.service.ItineraryAssemblyService.GeoPoint;

/**
 * High performance strategy interface for building, caching, and querying N x N route matrices.
 */
public interface RouteMatrixEngine {

    /**
     * Builds or retrieves from cache an immutable N x N RouteMatrix for a set of locations.
     *
     * @param context RouteMatrixContext containing location list and caching settings.
     * @return Fully populated RouteMatrix instance.
     */
    RouteMatrix buildMatrix(RouteMatrixContext context);

    /**
     * Lookups distance in kilometers between two points using the pre-computed matrix.
     */
    double getDistanceKm(RouteMatrix matrix, GeoPoint from, GeoPoint to);

    /**
     * Lookups duration in minutes between two points using the pre-computed matrix.
     */
    double getDurationMinutes(RouteMatrix matrix, GeoPoint from, GeoPoint to);

    /**
     * Lookups full entry (distance, duration, provider) between two points.
     */
    RouteMatrixEntry getEntry(RouteMatrix matrix, GeoPoint from, GeoPoint to);
}
