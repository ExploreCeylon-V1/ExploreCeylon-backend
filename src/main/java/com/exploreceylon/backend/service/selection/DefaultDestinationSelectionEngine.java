package com.exploreceylon.backend.service.selection;

import com.exploreceylon.backend.dto.budget.DayBudget;
import com.exploreceylon.backend.dto.routing.DistanceResult;
import com.exploreceylon.backend.dto.selection.SelectedStop;
import com.exploreceylon.backend.dto.selection.SelectionContext;
import com.exploreceylon.backend.dto.selection.SelectionStatistics;
import com.exploreceylon.backend.model.Destination;
import com.exploreceylon.backend.model.Destination.DestinationCategory;
import com.exploreceylon.backend.service.ItineraryAssemblyService.GeoPoint;
import com.exploreceylon.backend.service.ItineraryAssemblyService.TripDay;
import com.exploreceylon.backend.service.budget.VisitDurationEstimator;
import com.exploreceylon.backend.util.DistanceCalculator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Default implementation of DestinationSelectionEngine.
 * Allocates and schedules candidate destinations into daily trip itineraries,
 * balancing time budgets, category diversity, district progression, and timeline constraints.
 */
@Service
@Slf4j
public class DefaultDestinationSelectionEngine implements DestinationSelectionEngine {

    private final DistanceCalculator distanceCalculator;
    private final VisitDurationEstimator visitDurationEstimator;

    @Value("${planner.selection.walking-buffer-minutes:10}")
    private int walkingBufferMinutes = 10;

    @Value("${planner.selection.day-start-minutes:510}") // 08:30 AM = 510 minutes from 00:00
    private int dayStartMinutes = 510;

    @Value("${planner.category-limits.religious:2}")
    private int maxReligiousPerDay = 2;

    @Value("${planner.category-limits.museum:2}")
    private int maxMuseumPerDay = 2;

    @Value("${planner.category-limits.shopping:1}")
    private int maxShoppingPerDay = 1;

    @Value("${planner.category-limits.adventure:1}")
    private int maxAdventurePerDay = 1;

    @Value("${planner.category-limits.restaurant:2}")
    private int maxRestaurantPerDay = 2;

    public DefaultDestinationSelectionEngine(DistanceCalculator distanceCalculator,
                                              VisitDurationEstimator visitDurationEstimator) {
        this.distanceCalculator = distanceCalculator;
        this.visitDurationEstimator = visitDurationEstimator;
    }

    @Override
    public List<TripDay> selectAndScheduleDestinations(SelectionContext context) {
        if (context == null || context.getOrderedCandidates() == null || context.getOrderedCandidates().isEmpty()) {
            return List.of();
        }

        List<Destination> candidates = context.getOrderedCandidates();
        List<DayBudget> dayBudgets = context.getDayBudgets();
        int totalDays = (dayBudgets != null && !dayBudgets.isEmpty()) ? dayBudgets.size() : 1;

        Set<Long> visitedDestinationIds = new HashSet<>();
        Set<String> completedDistricts = new HashSet<>();

        List<TripDay> tripDays = new ArrayList<>();
        GeoPoint currentPos = context.getOrigin();

        for (int dayIdx = 0; dayIdx < totalDays; dayIdx++) {
            DayBudget budget = (dayBudgets != null && dayIdx < dayBudgets.size())
                    ? dayBudgets.get(dayIdx)
                    : DayBudget.builder().dayNumber(dayIdx + 1).availableSightseeingMinutes(390).maximumVisitCount(6).build();

            int currentClockMinutes = dayStartMinutes; // 08:30 AM
            int daySightseeingUsedMinutes = 0;
            int maxStopsForDay = budget.getMaximumVisitCount() > 0 ? budget.getMaximumVisitCount() : 6;

            Map<String, Integer> categoryUsage = new HashMap<>();
            List<Destination> dayDestinations = new ArrayList<>();
            Set<String> dayDistricts = new HashSet<>();

            for (Destination candidate : candidates) {
                if (visitedDestinationIds.contains(candidate.getId())) {
                    continue; // Already scheduled
                }

                if (dayDestinations.size() >= maxStopsForDay) {
                    break; // Reached max daily visits cap
                }

                String district = candidate.getDistrict();
                if (district != null && completedDistricts.contains(district.trim().toLowerCase(Locale.ROOT))
                        && !dayDistricts.contains(district.trim().toLowerCase(Locale.ROOT))) {
                    continue; // Enforce district progression (never return to a completed earlier district)
                }

                String categoryKey = getCategoryGroupKey(candidate.getCategory());
                int maxAllowedForCategory = getMaxCategoryLimit(categoryKey);
                int currentCatCount = categoryUsage.getOrDefault(categoryKey, 0);

                if (currentCatCount >= maxAllowedForCategory) {
                    continue; // Category limit reached, skip to promote diversity
                }

                GeoPoint candidatePos = new GeoPoint(candidate.getLatitude(), candidate.getLongitude());
                int drivingMinutes;
                if (context.getRouteMatrix() != null) {
                    drivingMinutes = (int) Math.round(context.getRouteMatrix().getEntry(currentPos, candidatePos).getDurationMinutes());
                } else {
                    DistanceResult travel = distanceCalculator.calculateRoute(
                            currentPos.lat(), currentPos.lng(),
                            candidate.getLatitude(), candidate.getLongitude()
                    );
                    drivingMinutes = travel != null ? (int) Math.round(travel.getDrivingDurationMinutes()) : 15;
                }
                int visitMinutes = visitDurationEstimator.estimateMinutes(candidate.getCategory());
                int totalRequiredMinutes = drivingMinutes + visitMinutes + walkingBufferMinutes;

                if ((daySightseeingUsedMinutes + totalRequiredMinutes) > budget.getAvailableSightseeingMinutes()) {
                    continue; // Exceeds available daily sightseeing budget
                }

                // Accept candidate for current day
                dayDestinations.add(candidate);
                visitedDestinationIds.add(candidate.getId());
                categoryUsage.put(categoryKey, currentCatCount + 1);

                if (district != null) {
                    dayDistricts.add(district.trim().toLowerCase(Locale.ROOT));
                }

                daySightseeingUsedMinutes += totalRequiredMinutes;
                currentPos = new GeoPoint(candidate.getLatitude(), candidate.getLongitude());
            }

            // Mark districts from previous days as completed to prevent backward district leaps
            completedDistricts.addAll(dayDistricts);

            tripDays.add(new TripDay(budget.getDayNumber(), dayDestinations, List.of(), List.of()));
        }

        log.info("DestinationSelectionEngine scheduled {} total destinations across {} days.",
                visitedDestinationIds.size(), tripDays.size());

        return tripDays;
    }

    @Override
    public SelectionStatistics evaluateDaySelection(TripDay day) {
        if (day == null || day.destinations() == null || day.destinations().isEmpty()) {
            return SelectionStatistics.builder()
                    .dailyQualityScore(0.0)
                    .categoryDiversityCount(0)
                    .averageRating(0.0)
                    .totalDrivingDistanceKm(0.0)
                    .unusedSightseeingMinutes(390)
                    .totalStopsCount(0)
                    .build();
        }

        List<Destination> dests = day.destinations();
        double avgRating = dests.stream().mapToDouble(d -> d.getRating() != null ? d.getRating() : 0.0).average().orElse(0.0);
        long categoryCount = dests.stream().map(Destination::getCategory).filter(Objects::nonNull).distinct().count();

        double diversityBonus = Math.min(30.0, categoryCount * 10.0);
        double ratingContribution = Math.min(50.0, (avgRating / 5.0) * 50.0);
        double stopCountBonus = Math.min(20.0, dests.size() * 5.0);

        double dailyQualityScore = Math.round((diversityBonus + ratingContribution + stopCountBonus) * 10.0) / 10.0;

        return SelectionStatistics.builder()
                .dailyQualityScore(dailyQualityScore)
                .categoryDiversityCount((int) categoryCount)
                .averageRating(Math.round(avgRating * 100.0) / 100.0)
                .totalDrivingDistanceKm(0.0)
                .unusedSightseeingMinutes(0)
                .totalStopsCount(dests.size())
                .build();
    }

    private String getCategoryGroupKey(DestinationCategory category) {
        if (category == null) return "OTHER";
        return category.name();
    }

    private int getMaxCategoryLimit(String categoryKey) {
        if (categoryKey.contains("RELIGIOUS") || categoryKey.contains("TEMPLE")) {
            return maxReligiousPerDay;
        } else if (categoryKey.contains("MUSEUM")) {
            return maxMuseumPerDay;
        } else if (categoryKey.contains("SHOPPING") || categoryKey.contains("CITY")) {
            return maxShoppingPerDay;
        } else if (categoryKey.contains("ADVENTURE")) {
            return maxAdventurePerDay;
        } else if (categoryKey.contains("RESTAURANT") || categoryKey.contains("FOOD")) {
            return maxRestaurantPerDay;
        }
        return 2;
    }
}
