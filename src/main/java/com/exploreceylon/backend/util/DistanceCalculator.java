package com.exploreceylon.backend.util;

/**
 * Strategy interface for calculating spatial distance between geographic coordinates.
 * Allows switching between Haversine spherical math and network-based road graph solvers (e.g. OSRM).
 */
public interface DistanceCalculator {

    /**
     * Calculates the distance in kilometers between two lat/lng points.
     *
     * @param lat1 Latitude of point 1
     * @param lng1 Longitude of point 1
     * @param lat2 Latitude of point 2
     * @param lng2 Longitude of point 2
     * @return Distance in kilometers
     */
    double calculateDistanceKm(double lat1, double lng1, double lat2, double lng2);
}
