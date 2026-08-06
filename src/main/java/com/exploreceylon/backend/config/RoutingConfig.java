package com.exploreceylon.backend.config;

import com.exploreceylon.backend.util.DistanceCalculator;
import com.exploreceylon.backend.util.HaversineDistanceCalculator;
import com.exploreceylon.backend.util.OsrmDistanceCalculator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Configuration factory providing the primary DistanceCalculator bean.
 * Reads `planner.routing.provider` property to select between:
 * - "osrm" -> OsrmDistanceCalculator (with Haversine fallback)
 * - "haversine" -> HaversineDistanceCalculator
 */
@Configuration
@Slf4j
public class RoutingConfig {

    @Bean
    @Primary
    public DistanceCalculator activeDistanceCalculator(
            @Value("${planner.routing.provider:osrm}") String routingProvider,
            OsrmDistanceCalculator osrmDistanceCalculator,
            HaversineDistanceCalculator haversineDistanceCalculator) {

        if ("haversine".equalsIgnoreCase(routingProvider)) {
            log.info("RoutingConfig: Selected HAVERSINE routing provider.");
            return haversineDistanceCalculator;
        }

        log.info("RoutingConfig: Selected OSRM routing provider with automatic Haversine fallback.");
        return osrmDistanceCalculator;
    }
}
