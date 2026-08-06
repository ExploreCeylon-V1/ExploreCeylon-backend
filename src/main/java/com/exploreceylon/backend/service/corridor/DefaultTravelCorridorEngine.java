package com.exploreceylon.backend.service.corridor;

import com.exploreceylon.backend.model.Destination;
import com.exploreceylon.backend.service.ItineraryAssemblyService.GeoPoint;
import com.exploreceylon.backend.util.DistanceCalculator;
import com.exploreceylon.backend.util.GeoUtils;
import com.exploreceylon.backend.util.PolylineDecoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Default implementation of TravelCorridorEngine.
 * Filters candidate destinations by building a spatial corridor along the OSRM road polyline geometry.
 */
@Service
@Slf4j
public class DefaultTravelCorridorEngine implements TravelCorridorEngine {

    private final DistanceCalculator distanceCalculator;

    @Value("${planner.corridor.enabled:true}")
    private boolean corridorEnabled = true;

    @Value("${planner.corridor.width-km:10.0}")
    private double defaultWidthKm = 10.0;

    @Value("${planner.corridor.max-detour-km:15.0}")
    private double defaultMaxDetourKm = 15.0;

    public DefaultTravelCorridorEngine(DistanceCalculator distanceCalculator) {
        this.distanceCalculator = distanceCalculator;
    }

    @Override
    public List<Destination> filterCandidates(List<Destination> candidates, CorridorContext context) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        boolean isEnabled = context != null ? context.isCorridorEnabled() : corridorEnabled;
        if (!isEnabled) {
            log.info("TravelCorridorEngine is disabled. Returning all {} raw candidates.", candidates.size());
            return candidates;
        }

        double widthKm = (context != null && context.getWidthKm() > 0.0) ? context.getWidthKm() : defaultWidthKm;
        double maxDetourKm = (context != null && context.getMaxDetourKm() > 0.0) ? context.getMaxDetourKm() : defaultMaxDetourKm;

        List<GeoPoint> routePath = extractRoutePath(context);
        if (routePath.isEmpty()) {
            log.warn("No route polyline path available for corridor filtering. Returning candidates unmodified.");
            return candidates;
        }

        GeoPoint origin = routePath.get(0);
        GeoPoint destination = routePath.get(routePath.size() - 1);
        double directDistance = distanceCalculator.calculateDistanceKm(origin.lat(), origin.lng(), destination.lat(), destination.lng());

        // Bounding box with margin for rapid pre-filtering
        double minLat = routePath.stream().mapToDouble(GeoPoint::lat).min().orElse(-90.0) - (widthKm / 111.0);
        double maxLat = routePath.stream().mapToDouble(GeoPoint::lat).max().orElse(90.0) + (widthKm / 111.0);
        double minLng = routePath.stream().mapToDouble(GeoPoint::lng).min().orElse(-180.0) - (widthKm / 111.0);
        double maxLng = routePath.stream().mapToDouble(GeoPoint::lng).max().orElse(180.0) + (widthKm / 111.0);

        List<Destination> corridorCandidates = candidates.stream().filter(d -> {
            if (d.getLatitude() == null || d.getLongitude() == null) return false;

            // 1. Quick Bounding Box Check
            if (d.getLatitude() < minLat || d.getLatitude() > maxLat
                    || d.getLongitude() < minLng || d.getLongitude() > maxLng) {
                return false;
            }

            // 2. Minimum Distance to Polyline Geometry Path
            double distToRoute = minDistanceToPath(d.getLatitude(), d.getLongitude(), routePath);
            if (distToRoute > widthKm) {
                return false;
            }

            // 3. Detour Tolerance Check
            double detourKm = calculateDetour(origin, destination, d.getLatitude(), d.getLongitude(), directDistance);
            return detourKm <= maxDetourKm;
        }).collect(Collectors.toList());

        log.info("TravelCorridorEngine filtered candidates from {} down to {} (Corridor Width: {}km, Max Detour: {}km)",
                candidates.size(), corridorCandidates.size(), widthKm, maxDetourKm);

        return corridorCandidates;
    }

    private List<GeoPoint> extractRoutePath(CorridorContext context) {
        if (context == null) return List.of();

        if (context.getRoutePath() != null && !context.getRoutePath().isEmpty()) {
            return context.getRoutePath();
        }

        if (context.getEncodedPolyline() != null && !context.getEncodedPolyline().isBlank()) {
            return PolylineDecoder.decode(context.getEncodedPolyline());
        }

        if (context.getOrigin() != null && context.getDestination() != null) {
            return List.of(context.getOrigin(), context.getDestination());
        }

        return List.of();
    }

    private double minDistanceToPath(double lat, double lng, List<GeoPoint> path) {
        double minDistance = Double.MAX_VALUE;
        for (int i = 0; i < path.size() - 1; i++) {
            GeoPoint p1 = path.get(i);
            GeoPoint p2 = path.get(i + 1);
            double dist = distanceToSegment(lat, lng, p1.lat(), p1.lng(), p2.lat(), p2.lng());
            if (dist < minDistance) {
                minDistance = dist;
            }
        }
        return minDistance;
    }

    private double distanceToSegment(double lat, double lng, double lat1, double lng1, double lat2, double lng2) {
        double dx = lng2 - lng1;
        double dy = lat2 - lat1;

        if (dx == 0 && dy == 0) {
            return GeoUtils.distanceKm(lat, lng, lat1, lng1);
        }

        double u = ((lng - lng1) * dx + (lat - lat1) * dy) / (dx * dx + dy * dy);

        if (u < 0) {
            return GeoUtils.distanceKm(lat, lng, lat1, lng1);
        } else if (u > 1) {
            return GeoUtils.distanceKm(lat, lng, lat2, lng2);
        } else {
            double projLat = lat1 + u * dy;
            double projLng = lng1 + u * dx;
            return GeoUtils.distanceKm(lat, lng, projLat, projLng);
        }
    }

    private double calculateDetour(GeoPoint origin, GeoPoint destination, double lat, double lng, double directKm) {
        double d1 = distanceCalculator.calculateDistanceKm(origin.lat(), origin.lng(), lat, lng);
        double d2 = distanceCalculator.calculateDistanceKm(lat, lng, destination.lat(), destination.lng());
        return (d1 + d2) - directKm;
    }
}
