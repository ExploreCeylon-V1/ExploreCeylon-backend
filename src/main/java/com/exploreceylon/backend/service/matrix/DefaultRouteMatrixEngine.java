package com.exploreceylon.backend.service.matrix;

import com.exploreceylon.backend.client.OsrmTableClient;
import com.exploreceylon.backend.client.OsrmTableClient.TableResponse;
import com.exploreceylon.backend.dto.matrix.MatrixStatistics;
import com.exploreceylon.backend.dto.matrix.RouteMatrix;
import com.exploreceylon.backend.dto.matrix.RouteMatrixContext;
import com.exploreceylon.backend.dto.matrix.RouteMatrixEntry;
import com.exploreceylon.backend.service.ItineraryAssemblyService.GeoPoint;
import com.exploreceylon.backend.util.DistanceCalculator;
import com.exploreceylon.backend.util.GeoUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Default implementation of RouteMatrixEngine.
 * Builds and caches N x N distance and duration matrices using OSRM Table API
 * with automatic fallback to Haversine matrices.
 */
@Service
@Slf4j
public class DefaultRouteMatrixEngine implements RouteMatrixEngine {

    private final OsrmTableClient osrmTableClient;
    private final DistanceCalculator distanceCalculator;
    private final Map<String, RouteMatrix> matrixCache = new ConcurrentHashMap<>();

    @Value("${planner.matrix.cache-enabled:true}")
    private boolean cacheEnabled = true;

    public DefaultRouteMatrixEngine(OsrmTableClient osrmTableClient, DistanceCalculator distanceCalculator) {
        this.osrmTableClient = osrmTableClient;
        this.distanceCalculator = distanceCalculator;
    }

    @Override
    public RouteMatrix buildMatrix(RouteMatrixContext context) {
        if (context == null || context.getLocations() == null || context.getLocations().isEmpty()) {
            return RouteMatrix.builder()
                    .distancesKm(new double[0][0])
                    .durationsMinutes(new double[0][0])
                    .locations(List.of())
                    .locationIndexMap(Map.of())
                    .providerUsed("EMPTY")
                    .build();
        }

        List<GeoPoint> locations = context.getLocations();
        String cacheKey = computeCacheKey(locations, context.getProfile());

        boolean shouldCache = context.isUseCache() && cacheEnabled;
        if (shouldCache && matrixCache.containsKey(cacheKey)) {
            log.info("RouteMatrixEngine cache HIT for key: {} ({} locations)", cacheKey, locations.size());
            RouteMatrix cached = matrixCache.get(cacheKey);
            cached.getStatistics().setCacheHit(true);
            return cached;
        }

        long startTime = System.currentTimeMillis();
        int n = locations.size();

        Map<String, Integer> indexMap = new HashMap<>();
        for (int i = 0; i < n; i++) {
            GeoPoint p = locations.get(i);
            indexMap.put(p.lat() + "," + p.lng(), i);
        }

        double[][] distancesKm = new double[n][n];
        double[][] durationsMinutes = new double[n][n];
        String providerUsed = "OSRM_TABLE";

        // Try OSRM Table API Batch Request
        TableResponse tableResp = osrmTableClient.fetchMatrix(locations);
        if (tableResp != null && tableResp.getDistances() != null && tableResp.getDurations() != null) {
            double[][] rawDist = tableResp.getDistances();
            double[][] rawDur = tableResp.getDurations();

            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    distancesKm[i][j] = Math.round((rawDist[i][j] / 1000.0) * 100.0) / 100.0;
                    durationsMinutes[i][j] = Math.round((rawDur[i][j] / 60.0) * 10.0) / 10.0;
                }
            }
        } else {
            // Fallback to Haversine matrix
            providerUsed = "HAVERSINE";
            log.warn("Falling back to Haversine distance matrix for {} locations.", n);
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    if (i == j) {
                        distancesKm[i][j] = 0.0;
                        durationsMinutes[i][j] = 0.0;
                    } else {
                        GeoPoint p1 = locations.get(i);
                        GeoPoint p2 = locations.get(j);
                        double dist = GeoUtils.distanceKm(p1.lat(), p1.lng(), p2.lat(), p2.lng());
                        distancesKm[i][j] = Math.round(dist * 100.0) / 100.0;
                        durationsMinutes[i][j] = Math.round((dist / 40.0 * 60.0) * 10.0) / 10.0;
                    }
                }
            }
        }

        long elapsedTime = System.currentTimeMillis() - startTime;
        MatrixStatistics stats = MatrixStatistics.builder()
                .locationCount(n)
                .buildTimeMs(elapsedTime)
                .providerUsed(providerUsed)
                .isCacheHit(false)
                .build();

        RouteMatrix matrix = RouteMatrix.builder()
                .distancesKm(distancesKm)
                .durationsMinutes(durationsMinutes)
                .locations(locations)
                .locationIndexMap(indexMap)
                .providerUsed(providerUsed)
                .cacheKey(cacheKey)
                .statistics(stats)
                .build();

        if (shouldCache) {
            matrixCache.put(cacheKey, matrix);
        }

        log.info("RouteMatrixEngine built {}x{} matrix using {} in {} ms.", n, n, providerUsed, elapsedTime);
        return matrix;
    }

    @Override
    public double getDistanceKm(RouteMatrix matrix, GeoPoint from, GeoPoint to) {
        if (matrix == null) return 0.0;
        return matrix.getEntry(from, to).getDistanceKm();
    }

    @Override
    public double getDurationMinutes(RouteMatrix matrix, GeoPoint from, GeoPoint to) {
        if (matrix == null) return 0.0;
        return matrix.getEntry(from, to).getDurationMinutes();
    }

    @Override
    public RouteMatrixEntry getEntry(RouteMatrix matrix, GeoPoint from, GeoPoint to) {
        if (matrix == null) return new RouteMatrixEntry(0.0, 0.0, "NONE");
        return matrix.getEntry(from, to);
    }

    private String computeCacheKey(List<GeoPoint> points, String profile) {
        try {
            String raw = points.stream()
                    .map(p -> p.lat() + "," + p.lng())
                    .collect(Collectors.joining(";")) + "|" + (profile != null ? profile : "driving");
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return "HASH_" + points.hashCode();
        }
    }
}
