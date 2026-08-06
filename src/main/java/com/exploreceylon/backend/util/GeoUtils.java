package com.exploreceylon.backend.util;

public final class GeoUtils {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private GeoUtils() {}

    // Great-circle distance between two lat/lng points, in kilometers.
    public static double distanceKm(double lat1, double lng1,
                                     double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    // Estimated road distance in kilometers using Haversine distance x 1.25 winding factor
    public static double roadDistanceKm(double lat1, double lng1, double lat2, double lng2) {
        return distanceKm(lat1, lng1, lat2, lng2) * 1.25;
    }

    // Projects (lat,lng) onto the line from (originLat,originLng) to (destLat,destLng)
    // using an equirectangular approximation (adequate at Sri Lanka's scale, ~500km).
    // Returns the distance in km from origin, measured ALONG the origin→destination
    // direction — negative if the point falls behind the origin, and greater than
    // distanceKm(origin,dest) if the point falls beyond the destination.
    public static double projectionAlongCorridorKm(double originLat, double originLng,
                                                     double destLat, double destLng,
                                                     double pointLat, double pointLng) {
        double avgLat = Math.toRadians((originLat + destLat) / 2.0);
        double kmPerDegLat = 110.574;
        double kmPerDegLng = 111.320 * Math.cos(avgLat);
        double dx = (destLng - originLng) * kmPerDegLng;
        double dy = (destLat - originLat) * kmPerDegLat;
        double px = (pointLng - originLng) * kmPerDegLng;
        double py = (pointLat - originLat) * kmPerDegLat;
        double routeLenSq = dx * dx + dy * dy;
        if (routeLenSq < 1e-9) return 0.0; // origin == destination
        double t = (px * dx + py * dy) / routeLenSq; // fraction along the route
        return t * Math.sqrt(routeLenSq);
    }
}
