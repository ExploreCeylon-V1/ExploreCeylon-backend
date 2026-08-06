package com.exploreceylon.backend.client;

import com.exploreceylon.backend.service.ItineraryAssemblyService.GeoPoint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST Client interacting with the OSRM Table API endpoint to retrieve batch distance and duration matrices.
 * URL format: GET /table/v1/driving/{lng1},{lat1};{lng2},{lat2};...?annotations=distance,duration
 */
@Component
@Slf4j
public class OsrmTableClient {

    private final RestTemplate restTemplate;

    @Value("${planner.osrm.base-url:https://router.project-osrm.org}")
    private String osrmBaseUrl;

    public OsrmTableClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(4000);
        this.restTemplate = new RestTemplate(factory);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TableResponse {
        private String code;
        private double[][] distances; // meters
        private double[][] durations; // seconds
    }

    /**
     * Calls OSRM Table API to fetch distance and duration matrices for all coordinates in a single HTTP request.
     *
     * @param points List of GeoPoints
     * @return TableResponse with distance and duration matrices
     */
    public TableResponse fetchMatrix(List<GeoPoint> points) {
        if (points == null || points.size() < 2) {
            return null;
        }

        String coordinatesStr = points.stream()
                .map(p -> p.lng() + "," + p.lat())
                .collect(Collectors.joining(";"));

        String url = String.format("%s/table/v1/driving/%s?annotations=distance,duration", osrmBaseUrl, coordinatesStr);

        try {
            log.info("Requesting OSRM Table matrix for {} locations...", points.size());
            TableResponse response = restTemplate.getForObject(url, TableResponse.class);
            if (response != null && "Ok".equalsIgnoreCase(response.getCode())) {
                return response;
            }
        } catch (Exception e) {
            log.warn("OSRM Table API request failed: {}. Falling back to Haversine matrix.", e.getMessage());
        }

        return null;
    }
}
