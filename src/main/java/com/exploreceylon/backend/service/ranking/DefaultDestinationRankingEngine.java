package com.exploreceylon.backend.service.ranking;

import com.exploreceylon.backend.model.Destination;
import com.exploreceylon.backend.service.ItineraryAssemblyService.GeoPoint;
import com.exploreceylon.backend.util.GeoUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Default implementation of DestinationRankingEngine.
 * Implements a weighted multi-factor scoring algorithm combining:
 * 1. Base Rating (Max 25 pts)
 * 2. Popularity & Review Count (Max 15 pts)
 * 3. Travel Style & Category Match (Max 30 pts)
 * 4. Seasonality Alignment (Max 10 pts)
 * 5. Special Distinction - Featured / UNESCO (Max 10 pts)
 * 6. Proximity Efficiency (Max 10 pts)
 */
@Service
@Slf4j
public class DefaultDestinationRankingEngine implements DestinationRankingEngine {

    private static final double MAX_RATING_POINTS = 25.0;
    private static final double MAX_POPULARITY_POINTS = 15.0;
    private static final double MAX_STYLE_POINTS = 30.0;
    private static final double MAX_SEASON_POINTS = 10.0;
    private static final double MAX_BONUS_POINTS = 10.0;
    private static final double MAX_PROXIMITY_POINTS = 10.0;

    @Override
    public double calculateScore(Destination destination, RankingContext context) {
        if (destination == null) return 0.0;

        double ratingScore = computeRatingScore(destination.getRating());
        double popularityScore = computePopularityScore(destination.getReviewCount());
        double styleScore = computeStyleScore(destination, context != null ? context.getTravelStyles() : null);
        double seasonScore = computeSeasonScore(destination.getBestMonths(), context != null ? context.getTripMonths() : null);
        double bonusScore = computeBonusScore(destination);
        double proximityScore = computeProximityScore(destination, context != null ? context.getCurrentPosition() : null);

        double totalScore = ratingScore + popularityScore + styleScore + seasonScore + bonusScore + proximityScore;
        return Math.min(100.0, Math.max(0.0, totalScore));
    }

    @Override
    public List<Destination> rankDestinations(List<Destination> candidates, RankingContext context) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        return candidates.stream()
                .sorted((d1, d2) -> Double.compare(
                        calculateScore(d2, context),
                        calculateScore(d1, context)
                ))
                .collect(Collectors.toList());
    }

    private double computeRatingScore(Double rating) {
        if (rating == null || rating <= 0.0) return 0.0;
        double clamped = Math.min(5.0, Math.max(0.0, rating));
        return (clamped / 5.0) * MAX_RATING_POINTS;
    }

    private double computePopularityScore(Integer reviewCount) {
        if (reviewCount == null || reviewCount <= 0) return 0.0;
        // Logarithmic scaling: ~100 reviews hits 15 points
        double logScore = Math.log(1.0 + reviewCount) / Math.log(101.0);
        return Math.min(1.0, logScore) * MAX_POPULARITY_POINTS;
    }

    private double computeStyleScore(Destination destination, List<String> userStyles) {
        if (userStyles == null || userStyles.isEmpty()) {
            return 15.0; // Baseline neutral match
        }

        Set<String> targetStyles = userStyles.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(s -> s.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());

        if (targetStyles.isEmpty()) return 15.0;

        // Check travel style tags CSV
        String csvTags = destination.getTravelStyleTags();
        if (csvTags != null && !csvTags.isBlank()) {
            Set<String> destTags = Arrays.stream(csvTags.split(","))
                    .map(s -> s.trim().toUpperCase(Locale.ROOT))
                    .collect(Collectors.toSet());
            boolean directMatch = destTags.stream().anyMatch(targetStyles::contains);
            if (directMatch) return MAX_STYLE_POINTS; // 30 pts
        }

        // Check category match
        if (destination.getCategory() != null) {
            String catName = destination.getCategory().name().toUpperCase(Locale.ROOT);
            if (targetStyles.contains(catName)) {
                return 22.5; // Partial match on category
            }
        }

        return 7.5; // Mild fallback for non-matching style
    }

    private double computeSeasonScore(String bestMonthsCsv, Set<String> tripMonths) {
        if (bestMonthsCsv == null || bestMonthsCsv.isBlank() || tripMonths == null || tripMonths.isEmpty()) {
            return 7.0; // Unspecified seasonality defaults to decent baseline
        }

        Set<String> bestMonths = Arrays.stream(bestMonthsCsv.split(","))
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        boolean isSeasonMatch = bestMonths.stream().anyMatch(tripMonths::contains);
        return isSeasonMatch ? MAX_SEASON_POINTS : 3.0;
    }

    private double computeBonusScore(Destination destination) {
        double bonus = 0.0;
        if (Boolean.TRUE.equals(destination.getFeatured())) {
            bonus += 5.0;
        }
        if (destination.getUnescoStatus() != null && !destination.getUnescoStatus().isBlank()) {
            bonus += 5.0;
        }
        return Math.min(MAX_BONUS_POINTS, bonus);
    }

    private double computeProximityScore(Destination destination, GeoPoint currentPos) {
        if (currentPos == null || destination.getLatitude() == null || destination.getLongitude() == null) {
            return 5.0; // Neutral fallback when spatial context not provided
        }

        double distKm = GeoUtils.distanceKm(currentPos.lat(), currentPos.lng(),
                destination.getLatitude(), destination.getLongitude());

        // Exponential decay: 0km -> 10pts, 50km -> ~3.6pts, 100km -> ~1.3pts
        double decay = Math.exp(-distKm / 50.0);
        return Math.min(MAX_PROXIMITY_POINTS, Math.max(0.0, decay * MAX_PROXIMITY_POINTS));
    }
}
