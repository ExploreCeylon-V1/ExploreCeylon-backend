package com.exploreceylon.backend.service.budget;

import com.exploreceylon.backend.model.Destination.DestinationCategory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Reusable service for estimating typical visitor duration in minutes based on destination category.
 * Driven by configurable Spring properties (planner.visit-duration.*).
 */
@Service
@Slf4j
public class VisitDurationEstimator {

    @Value("${planner.visit-duration.temple:45}")
    private int templeDuration = 45;

    @Value("${planner.visit-duration.museum:90}")
    private int museumDuration = 90;

    @Value("${planner.visit-duration.historical:60}")
    private int historicalDuration = 60;

    @Value("${planner.visit-duration.viewpoint:20}")
    private int viewpointDuration = 20;

    @Value("${planner.visit-duration.waterfall:60}")
    private int waterfallDuration = 60;

    @Value("${planner.visit-duration.botanical-garden:90}")
    private int botanicalGardenDuration = 90;

    @Value("${planner.visit-duration.beach:90}")
    private int beachDuration = 90;

    @Value("${planner.visit-duration.zoo:180}")
    private int zooDuration = 180;

    @Value("${planner.visit-duration.shopping:60}")
    private int shoppingDuration = 60;

    @Value("${planner.visit-duration.tea-factory:45}")
    private int teaFactoryDuration = 45;

    @Value("${planner.visit-duration.adventure:150}")
    private int adventureDuration = 150;

    @Value("${planner.visit-duration.restaurant:60}")
    private int restaurantDuration = 60;

    @Value("${planner.visit-duration.default:45}")
    private int defaultDuration = 45;

    /**
     * Estimates visit duration in minutes for a given destination category enum.
     */
    public int estimateMinutes(DestinationCategory category) {
        if (category == null) return defaultDuration;
        return estimateMinutes(category.name());
    }

    /**
     * Estimates visit duration in minutes for a given category name string.
     */
    public int estimateMinutes(String categoryName) {
        if (categoryName == null || categoryName.isBlank()) {
            return defaultDuration;
        }

        String cat = categoryName.trim().toLowerCase(Locale.ROOT);

        if (cat.contains("temple") || cat.contains("religious")) {
            return templeDuration;
        } else if (cat.contains("museum")) {
            return museumDuration;
        } else if (cat.contains("cultural") || cat.contains("heritage") || cat.contains("historical")) {
            return historicalDuration;
        } else if (cat.contains("viewpoint") || cat.contains("view")) {
            return viewpointDuration;
        } else if (cat.contains("waterfall")) {
            return waterfallDuration;
        } else if (cat.contains("botanical") || cat.contains("garden") || cat.contains("hill")) {
            return botanicalGardenDuration;
        } else if (cat.contains("beach") || cat.contains("surf")) {
            return beachDuration;
        } else if (cat.contains("zoo") || cat.contains("wildlife")) {
            return zooDuration;
        } else if (cat.contains("shop") || cat.contains("city")) {
            return shoppingDuration;
        } else if (cat.contains("tea")) {
            return teaFactoryDuration;
        } else if (cat.contains("adventure")) {
            return adventureDuration;
        } else if (cat.contains("restaurant") || cat.contains("food")) {
            return restaurantDuration;
        }

        return defaultDuration;
    }
}
