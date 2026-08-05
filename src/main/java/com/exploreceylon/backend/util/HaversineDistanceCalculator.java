package com.exploreceylon.backend.util;

import com.exploreceylon.backend.dto.routing.DistanceResult;
import org.springframework.stereotype.Component;

/**
 * Implementation of DistanceCalculator using standard Haversine spherical math.
 */
@Component("haversineDistanceCalculator")
public class HaversineDistanceCalculator implements DistanceCalculator {

    private static final double AVG_SPEED_KMH = 40.0;

    @Override
    public double calculateDistanceKm(double lat1, double lng1, double lat2, double lng2) {
        return GeoUtils.distanceKm(lat1, lng1, lat2, lng2);
    }

    @Override
    public DistanceResult calculateRoute(double lat1, double lng1, double lat2, double lng2) {
        double distKm = calculateDistanceKm(lat1, lng1, lat2, lng2);
        int durationMin = (int) Math.round((distKm / AVG_SPEED_KMH) * 60.0);

        return DistanceResult.builder()
                .drivingDistanceKm(distKm)
                .drivingDurationMinutes(durationMin)
                .encodedPolyline(null)
                .providerUsed("HAVERSINE")
                .success(true)
                .build();
    }
}
