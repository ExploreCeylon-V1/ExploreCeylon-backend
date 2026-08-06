package com.exploreceylon.backend.util;

import com.exploreceylon.backend.dto.routing.DistanceResult;

/**
 * Strategy interface for calculating spatial distance and travel duration between geographic coordinates.
 * Allows switching between Haversine spherical math and network-based road graph solvers (e.g. OSRM).
 */
public interface DistanceCalculator {

    /**
     * Calculates the full routing details (distance, duration, provider, status) between two coordinates.
     *
     * @param lat1 Latitude of point 1
     * @param lng1 Longitude of point 1
     * @param lat2 Latitude of point 2
     * @param lng2 Longitude of point 2
     * @return DistanceResult object
     */
    DistanceResult calculateRoute(double lat1, double lng1, double lat2, double lng2);

    /**
     * Calculates the distance in kilometers between two lat/lng points.
     * Default implementation delegates to calculateRoute().
     *
     * @param lat1 Latitude of point 1
     * @param lng1 Longitude of point 1
     * @param lat2 Latitude of point 2
     * @param lng2 Longitude of point 2
     * @return Distance in kilometers
     */
    default double calculateDistanceKm(double lat1, double lng1, double lat2, double lng2) {
        DistanceResult res = calculateRoute(lat1, lng1, lat2, lng2);
        return res != null ? res.getDrivingDistanceKm() : GeoUtils.distanceKm(lat1, lng1, lat2, lng2);
    }
}
