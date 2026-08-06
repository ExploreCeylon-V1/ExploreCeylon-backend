package com.exploreceylon.backend.util;

import com.exploreceylon.backend.client.OsrmClient;
import com.exploreceylon.backend.dto.routing.DistanceResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OSRM implementation of DistanceCalculator.
 * Features in-memory route caching and automatic fallback to HaversineDistanceCalculator
 * if OSRM service is unavailable, slow, or fails.
 */
@Component("osrmDistanceCalculator")
@Slf4j
public class OsrmDistanceCalculator implements DistanceCalculator {

    private final OsrmClient osrmClient;
    private final HaversineDistanceCalculator fallbackCalculator;
    private final Map<String, DistanceResult> routeCache = new ConcurrentHashMap<>();

    public OsrmDistanceCalculator(OsrmClient osrmClient, HaversineDistanceCalculator fallbackCalculator) {
        this.osrmClient = osrmClient;
        this.fallbackCalculator = fallbackCalculator;
    }

    @Override
    public DistanceResult calculateRoute(double lat1, double lng1, double lat2, double lng2) {
        String cacheKey = buildCacheKey(lat1, lng1, lat2, lng2);

        if (routeCache.containsKey(cacheKey)) {
            log.debug("Cache hit for route key: {}", cacheKey);
            return routeCache.get(cacheKey);
        }

        log.debug("Cache miss for route key: {}. Querying OSRM Client...", cacheKey);
        DistanceResult osrmResult = osrmClient.getRoute(lat1, lng1, lat2, lng2);

        if (osrmResult != null && osrmResult.isSuccess()) {
            routeCache.put(cacheKey, osrmResult);
            return osrmResult;
        }

        log.warn("OSRM routing unavailable for ({},{}) -> ({},{}). Falling back to Haversine calculation.",
                lat1, lng1, lat2, lng2);
        
        DistanceResult fallbackResult = fallbackCalculator.calculateRoute(lat1, lng1, lat2, lng2);
        // Cache fallback result temporarily to avoid hammering failing endpoint
        routeCache.put(cacheKey, fallbackResult);
        return fallbackResult;
    }

    @Override
    public double calculateDistanceKm(double lat1, double lng1, double lat2, double lng2) {
        DistanceResult res = calculateRoute(lat1, lng1, lat2, lng2);
        return res != null ? res.getDrivingDistanceKm() : GeoUtils.distanceKm(lat1, lng1, lat2, lng2);
    }

    public void clearCache() {
        routeCache.clear();
    }

    public int getCacheSize() {
        return routeCache.size();
    }

    private String buildCacheKey(double lat1, double lng1, double lat2, double lng2) {
        return String.format(Locale.ROOT, "%.4f,%.4f->%.4f,%.4f", lat1, lng1, lat2, lng2);
    }
}
