package com.exploreceylon.backend.service.progression;

import com.exploreceylon.backend.dto.progression.JourneyProgress;
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
 * Default implementation of JourneyProgressionEngine.
 * Projects candidate destinations onto the travelled OSRM road route polyline geometry,
 * calculates cumulative progress distance from origin, and orders candidates forward.
 */
@Service
@Slf4j
public class DefaultJourneyProgressionEngine implements JourneyProgressionEngine {

    private final DistanceCalculator distanceCalculator;

    @Value("${planner.progression.enabled:true}")
    private boolean progressionEnabled = true;

    @Value("${planner.progression.minimum-forward-distance-km:2.0}")
    private double defaultMinForwardKm = 2.0;

    @Value("${planner.progression.allow-backtracking:false}")
    private boolean allowBacktracking = false;

    public DefaultJourneyProgressionEngine(DistanceCalculator distanceCalculator) {
        this.distanceCalculator = distanceCalculator;
    }

    @Override
    public List<Destination> orderCandidatesByProgress(List<Destination> candidates, ProgressionContext context) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        boolean isEnabled = context != null ? context.isProgressionEnabled() : progressionEnabled;
        if (!isEnabled) {
            log.info("JourneyProgressionEngine is disabled. Returning candidates in original order.");
            return candidates;
        }

        return candidates.stream()
                .sorted((d1, d2) -> {
                    JourneyProgress p1 = calculateProgress(d1, context);
                    JourneyProgress p2 = calculateProgress(d2, context);
                    return Double.compare(p1.getProgressDistanceKm(), p2.getProgressDistanceKm());
                })
                .collect(Collectors.toList());
    }

    @Override
    public JourneyProgress calculateProgress(Destination destination, ProgressionContext context) {
        if (destination == null || destination.getLatitude() == null || destination.getLongitude() == null) {
            return JourneyProgress.builder()
                    .destinationId(destination != null ? destination.getId() : null)
                    .progressDistanceKm(0.0)
                    .remainingDistanceKm(0.0)
                    .routeSegmentIndex(0)
                    .build();
        }

        List<GeoPoint> routePath = extractRoutePath(context);
        if (routePath.size() < 2) {
            // Fallback: simple linear progress if polyline is unavailable
            GeoPoint origin = (context != null && context.getOrigin() != null) ? context.getOrigin() : new GeoPoint(destination.getLatitude(), destination.getLongitude());
            GeoPoint destPoint = (context != null && context.getDestination() != null) ? context.getDestination() : origin;
            
            double progress = distanceCalculator.calculateDistanceKm(origin.lat(), origin.lng(), destination.getLatitude(), destination.getLongitude());
            double total = distanceCalculator.calculateDistanceKm(origin.lat(), origin.lng(), destPoint.lat(), destPoint.lng());

            return JourneyProgress.builder()
                    .destinationId(destination.getId())
                    .progressDistanceKm(progress)
                    .remainingDistanceKm(Math.max(0.0, total - progress))
                    .routeSegmentIndex(0)
                    .build();
        }

        // Pre-compute cumulative polyline segment distances
        double[] cumulativeDist = new double[routePath.size()];
        cumulativeDist[0] = 0.0;
        for (int i = 1; i < routePath.size(); i++) {
            GeoPoint pPrev = routePath.get(i - 1);
            GeoPoint pCurr = routePath.get(i);
            cumulativeDist[i] = cumulativeDist[i - 1] + GeoUtils.distanceKm(pPrev.lat(), pPrev.lng(), pCurr.lat(), pCurr.lng());
        }
        double totalRouteKm = cumulativeDist[routePath.size() - 1];

        // Find nearest segment and projection factor u
        int bestSegmentIndex = 0;
        double bestMinDistance = Double.MAX_VALUE;
        double bestSegmentProgressFactor = 0.0;

        double dLat = destination.getLatitude();
        double dLng = destination.getLongitude();

        for (int i = 0; i < routePath.size() - 1; i++) {
            GeoPoint v1 = routePath.get(i);
            GeoPoint v2 = routePath.get(i + 1);

            double dx = v2.lng() - v1.lng();
            double dy = v2.lat() - v1.lat();

            double u = 0.0;
            if (dx != 0 || dy != 0) {
                u = ((dLng - v1.lng()) * dx + (dLat - v1.lat()) * dy) / (dx * dx + dy * dy);
                u = Math.max(0.0, Math.min(1.0, u));
            }

            double projLat = v1.lat() + u * dy;
            double projLng = v1.lng() + u * dx;
            double distToSegment = GeoUtils.distanceKm(dLat, dLng, projLat, projLng);

            if (distToSegment < bestMinDistance) {
                bestMinDistance = distToSegment;
                bestSegmentIndex = i;
                bestSegmentProgressFactor = u;
            }
        }

        double segmentLength = cumulativeDist[bestSegmentIndex + 1] - cumulativeDist[bestSegmentIndex];
        double progressDistanceKm = cumulativeDist[bestSegmentIndex] + (bestSegmentProgressFactor * segmentLength);
        double remainingDistanceKm = Math.max(0.0, totalRouteKm - progressDistanceKm);

        return JourneyProgress.builder()
                .destinationId(destination.getId())
                .progressDistanceKm(progressDistanceKm)
                .remainingDistanceKm(remainingDistanceKm)
                .routeSegmentIndex(bestSegmentIndex)
                .build();
    }

    private List<GeoPoint> extractRoutePath(ProgressionContext context) {
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
}
