package com.exploreceylon.backend.util;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Primary implementation of DistanceCalculator using standard Haversine spherical math.
 */
@Component("haversineDistanceCalculator")
@Primary
public class HaversineDistanceCalculator implements DistanceCalculator {

    @Override
    public double calculateDistanceKm(double lat1, double lng1, double lat2, double lng2) {
        return GeoUtils.distanceKm(lat1, lng1, lat2, lng2);
    }
}
