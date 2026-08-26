package com.exploreceylon.backend.service.ranking;

import com.exploreceylon.backend.model.Destination;
import com.exploreceylon.backend.service.ItineraryAssemblyService.GeoPoint;
import com.exploreceylon.backend.util.DistanceCalculator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Default implementation of DestinationRankingEngine.
 * Implements a weighted multi-factor scoring algorithm driven by configurable weights:
 * 1. Base Rating (ratingWeight, default 25.0)
 * 2. Popularity & Review Count (popularityWeight, default 15.0)
 * 3. Travel Style & Category Match (styleWeight, default 30.0)
 * 4. Seasonality Alignment (seasonWeight, default 10.0)
 * 5. Special Distinction - Featured / UNESCO (distinctionWeight, default 10.0)
 * 6. Proximity Efficiency (proximityWeight, default 10.0)
 */
@Service
@Slf4j
public class DefaultDestinationRankingEngine implements DestinationRankingEngine {

    private final DistanceCalculator distanceCalculator;

    @Value("${planner.ranking.rating-weight:25.0}")
    private double ratingWeight = 25.0;

    @Value("${planner.ranking.popularity-weight:15.0}")
    private double popularityWeight = 15.0;

    @Value("${planner.ranking.style-weight:30.0}")
    private double styleWeight = 30.0;

    @Value("${planner.ranking.season-weight:10.0}")
    private double seasonWeight = 10.0;

    @Value("${planner.ranking.distinction-weight:10.0}")
    private double distinctionWeight = 10.0;

    @Value("${planner.ranking.proximity-weight:10.0}")
    private double proximityWeight = 10.0;

    public DefaultDestinationRankingEngine(DistanceCalculator distanceCalculator) {
        this.distanceCalculator = distanceCalculator;
    }

    @Override
    public double calculateScore(Destination destination, RankingContext context) {
        return explainScore(destination, context).getFinalScore();
    }

    @Override
    public DestinationScore explainScore(Destination destination, RankingContext context) {
        if (destination == null) {
            return DestinationScore.builder()
                    .finalScore(0.0)
                    .matchingStyles(Set.of())
                    .build();
        }

        double ratingScore = computeRatingScore(destination.getRating());
        double popularityScore = computePopularityScore(destination.getReviewCount());
        
        Set<String> matchingStyles = new HashSet<>();
        double styleScore = computeStyleScore(destination, context != null ? context.getTravelStyles() : null, matchingStyles);
        
        boolean seasonMatch = false;
        double seasonScore = computeSeasonScore(destination.getBestMonths(), context != null ? context.getTripMonths() : null);
        if (context != null && context.getTripMonths() != null && !context.getTripMonths().isEmpty()
                && destination.getBestMonths() != null && !destination.getBestMonths().isBlank()) {
            Set<String> bestMonths = Arrays.stream(destination.getBestMonths().split(","))
                    .map(s -> s.trim().toLowerCase(Locale.ROOT))
                    .collect(Collectors.toSet());
            seasonMatch = bestMonths.stream().anyMatch(context.getTripMonths()::contains);
        }

        double bonusScore = computeBonusScore(destination);
        
        Double distKm = null;
        GeoPoint curPos = context != null ? context.getCurrentPosition() : null;
        if (curPos != null && destination.getLatitude() != null && destination.getLongitude() != null) {
            distKm = distanceCalculator.calculateDistanceKm(curPos.lat(), curPos.lng(),
                    destination.getLatitude(), destination.getLongitude());
        }

        double proximityScore = computeProximityScore(distKm);

        double totalScore = ratingScore + popularityScore + styleScore + seasonScore + bonusScore + proximityScore;
        double finalScore = Math.min(100.0, Math.max(0.0, totalScore));

        DestinationScore scoreExplanation = DestinationScore.builder()
                .destinationId(destination.getId())
                .destinationName(destination.getName())
                .finalScore(finalScore)
                .ratingScore(ratingScore)
                .popularityScore(popularityScore)
                .styleScore(styleScore)
                .seasonScore(seasonScore)
                .bonusScore(bonusScore)
                .proximityScore(proximityScore)
                .matchingStyles(matchingStyles)
                .seasonMatch(seasonMatch)
                .distanceKm(distKm)
                .build();

        log.debug("Evaluated DestinationScore: {}", scoreExplanation);
        return scoreExplanation;
    }

    @Override
    public List<Destination> rankDestinations(List<Destination> candidates, RankingContext context) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        record ScoredCandidate(Destination destination, double score) {}

        return candidates.stream()
                .map(d -> new ScoredCandidate(d, calculateScore(d, context)))
                .sorted((c1, c2) -> Double.compare(c2.score(), c1.score()))
                .map(ScoredCandidate::destination)
                .collect(Collectors.toList());
    }

    private double computeRatingScore(Double rating) {
        if (rating == null || rating <= 0.0) return 0.0;
        double clamped = Math.min(5.0, Math.max(0.0, rating));
        return (clamped / 5.0) * ratingWeight;
    }

    private double computePopularityScore(Integer reviewCount) {
        if (reviewCount == null || reviewCount <= 0) return 0.0;
        double logScore = Math.log(1.0 + reviewCount) / Math.log(101.0);
        return Math.min(1.0, logScore) * popularityWeight;
    }

    private double computeStyleScore(Destination destination, List<String> userStyles, Set<String> matchingStylesOutput) {
        if (userStyles == null || userStyles.isEmpty()) {
            return styleWeight * 0.5; // Baseline neutral match (50%)
        }

        Set<String> targetStyles = userStyles.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(s -> s.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());

        if (targetStyles.isEmpty()) return styleWeight * 0.5;

        if (destination.getCategory() != null) {
            String catName = destination.getCategory().name().toUpperCase(Locale.ROOT);
            if (targetStyles.contains(catName)) {
                if (matchingStylesOutput != null) matchingStylesOutput.add(catName);
                return styleWeight; // 100% — category is the authoritative unified match
            }
        }

        String csvTags = destination.getTravelStyleTags();
        if (csvTags != null && !csvTags.isBlank()) {
            Set<String> destTags = Arrays.stream(csvTags.split(","))
                    .map(s -> s.trim().toUpperCase(Locale.ROOT))
                    .collect(Collectors.toSet());
            Set<String> matches = destTags.stream()
                    .filter(targetStyles::contains)
                    .collect(Collectors.toSet());
            if (!matches.isEmpty()) {
                if (matchingStylesOutput != null) matchingStylesOutput.addAll(matches);
                return styleWeight * 0.75; // 75% secondary/legacy-tag signal
            }
        }

        return styleWeight * 0.25; // 25% non-matching fallback
    }

    private double computeSeasonScore(String bestMonthsCsv, Set<String> tripMonths) {
        if (bestMonthsCsv == null || bestMonthsCsv.isBlank() || tripMonths == null || tripMonths.isEmpty()) {
            return seasonWeight * 0.7; // Unspecified seasonality defaults to 70%
        }

        Set<String> bestMonths = Arrays.stream(bestMonthsCsv.split(","))
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
        
        .collect(Collectors.toSet());

        boolean isSeasonMatch = bestMonths.stream().anyMatch(tripMonths::contains);
        return isSeasonMatch ? seasonWeight : seasonWeight * 0.3;
    }

    private double computeBonusScore(Destination destination) {
        double bonus = 0.0;
        if (Boolean.TRUE.equals(destination.getFeatured())) {
            bonus += distinctionWeight * 0.5;
        }
        if (destination.getUnescoStatus() != null && !destination.getUnescoStatus().isBlank()) {
            bonus += distinctionWeight * 0.5;
        }
        return Math.min(distinctionWeight, bonus);
    }

    private double computeProximityScore(Double distKm) {
        if (distKm == null) {
            return proximityWeight * 0.5; // Neutral fallback
        }
        double decay = Math.exp(-distKm / 50.0);
        return Math.min(proximityWeight, Math.max(0.0, decay * proximityWeight));
    }
}
