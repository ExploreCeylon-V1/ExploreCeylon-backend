package com.exploreceylon.backend.client;

import com.exploreceylon.backend.dto.routing.DistanceResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Locale;

/**
 * Dedicated REST client for querying the Open Source Routing Machine (OSRM) API.
 */
@Component
@Slf4j
public class OsrmClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${planner.osrm.base-url:https://router.project-osrm.org}")
    private String baseUrl;

    @Value("${planner.osrm.profile:driving}")
    private String profile;

    @Value("${planner.osrm.timeout-ms:600}")
    private int timeoutMs;

    public OsrmClient() {
        this.objectMapper = new ObjectMapper();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(600);
        factory.setReadTimeout(600);
        this.restTemplate = new RestTemplate(factory);
    }

    public OsrmClient(RestTemplate restTemplate, String baseUrl, String profile) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.profile = profile;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Queries OSRM route API for driving distance, duration, and optional polyline.
     * Note: OSRM expects coordinates in {longitude},{latitude} format.
     */
    public DistanceResult getRoute(double lat1, double lng1, double lat2, double lng2) {
        return getRoute(lat1, lng1, lat2, lng2, false);
    }

    public DistanceResult getRoute(double lat1, double lng1, double lat2, double lng2, boolean includeOverview) {
        String overviewParam = includeOverview ? "overview=simplified" : "overview=false";
        String coordsStr = String.format(Locale.ROOT, "%.6f,%.6f;%.6f,%.6f", lng1, lat1, lng2, lat2);
        String url = String.format(Locale.ROOT, "%s/route/v1/%s/%s?%s", baseUrl, profile, coordsStr, overviewParam);

        try {
            log.debug("Executing OSRM route query: {}", url);
            String rawJson = restTemplate.getForObject(url, String.class);
            if (rawJson == null || rawJson.isBlank()) {
                log.warn("OSRM API returned empty response for URL: {}", url);
                return null;
            }

            JsonNode root = objectMapper.readTree(rawJson);
            String code = root.path("code").asText("");

            if (!"Ok".equalsIgnoreCase(code)) {
                log.warn("OSRM API response code was not 'Ok': {}", code);
                return null;
            }

            JsonNode routesNode = root.path("routes");
            if (!routesNode.isArray() || routesNode.isEmpty()) {
                log.warn("OSRM API returned no route entries");
                return null;
            }

            JsonNode primaryRoute = routesNode.get(0);
            double distanceMeters = primaryRoute.path("distance").asDouble(0.0);
            double durationSeconds = primaryRoute.path("duration").asDouble(0.0);
            String geometry = primaryRoute.path("geometry").asText(null);

            double distanceKm = Math.round((distanceMeters / 1000.0) * 100.0) / 100.0;
            int durationMinutes = (int) Math.round(durationSeconds / 60.0);

            return DistanceResult.builder()
                    .drivingDistanceKm(distanceKm)
                    .drivingDurationMinutes(durationMinutes)
                    .encodedPolyline(geometry)
                    .providerUsed("OSRM")
                    .success(true)
                    .build();

        } catch (Exception e) {
            log.warn("OSRM Client request failed for coords ({},{}) -> ({},{}): {}",
                    lat1, lng1, lat2, lng2, e.getMessage());
            return null;
        }
    }
}
