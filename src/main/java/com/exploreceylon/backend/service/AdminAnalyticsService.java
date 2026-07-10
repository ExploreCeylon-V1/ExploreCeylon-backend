package com.exploreceylon.backend.service;

import com.exploreceylon.backend.dto.admin.AnalyticsResponse;
import com.exploreceylon.backend.dto.admin.TopListsResponse;
import com.exploreceylon.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

// Every chart here is built from a single grouped-aggregate query per
// metric (see the countGroupedByMonth()/ratingDistribution() repository
// methods) — none of them load full tables into Java, matching the same
// DB-level approach used for the admin list endpoints.
@Service
@RequiredArgsConstructor
public class AdminAnalyticsService {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final UserRepository userRepository;
    private final TripRepository tripRepository;
    private final VehicleBookingRepository vehicleBookingRepository;
    private final GuideBookingRepository guideBookingRepository;
    private final DestinationRepository destinationRepository;
    private final AdminReviewQueryRepository adminReviewQueryRepository;

    public AnalyticsResponse getAnalytics() {
        Map<String, Long> registrationsByMonth = groupByMonth(userRepository.countGroupedByMonth());
        Map<String, Long> tripsByMonth = groupByMonth(tripRepository.countGroupedByMonth());
        Map<String, Long> vehicleBookingsByMonth = groupByMonth(vehicleBookingRepository.countGroupedByMonth());
        Map<String, Long> guideBookingsByMonth = groupByMonth(guideBookingRepository.countGroupedByMonth());

        List<String> months = last12MonthKeys();

        List<AnalyticsResponse.MonthPoint> monthlyRegistrations = months.stream()
                .map(m -> AnalyticsResponse.MonthPoint.builder().month(m).count(registrationsByMonth.getOrDefault(m, 0L)).build())
                .collect(Collectors.toList());

        List<AnalyticsResponse.MonthPoint> monthlyTrips = months.stream()
                .map(m -> AnalyticsResponse.MonthPoint.builder().month(m).count(tripsByMonth.getOrDefault(m, 0L)).build())
                .collect(Collectors.toList());

        List<AnalyticsResponse.MonthlyBookings> monthlyBookings = months.stream()
                .map(m -> AnalyticsResponse.MonthlyBookings.builder()
                        .month(m)
                        .vehicleCount(vehicleBookingsByMonth.getOrDefault(m, 0L))
                        .guideCount(guideBookingsByMonth.getOrDefault(m, 0L))
                        .build())
                .collect(Collectors.toList());

        List<AnalyticsResponse.RatingCount> reviewDistribution = new ArrayList<>();
        Map<Integer, Long> ratingCounts = new HashMap<>();
        for (Object[] row : adminReviewQueryRepository.ratingDistribution()) {
            if (row[0] != null) {
                ratingCounts.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue());
            }
        }
        for (int star = 1; star <= 5; star++) {
            reviewDistribution.add(AnalyticsResponse.RatingCount.builder()
                    .rating(star).count(ratingCounts.getOrDefault(star, 0L)).build());
        }

        List<TopListsResponse.TopDestination> destinationPopularity = destinationRepository
                .findTop10ByOrderByReviewCountDesc().stream()
                .map(d -> TopListsResponse.TopDestination.builder()
                        .id(d.getId()).name(d.getName())
                        .rating(d.getRating()).reviewCount(d.getReviewCount())
                        .build())
                .collect(Collectors.toList());

        return AnalyticsResponse.builder()
                .monthlyRegistrations(monthlyRegistrations)
                .monthlyTrips(monthlyTrips)
                .monthlyBookings(monthlyBookings)
                .reviewDistribution(reviewDistribution)
                .destinationPopularity(destinationPopularity)
                .build();
    }

    private Map<String, Long> groupByMonth(List<Object[]> rows) {
        Map<String, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            if (row[0] == null) continue;
            // Hibernate returns FUNCTION('date_trunc', ...) as the entity
            // field's own mapped Java type (LocalDateTime here), not a raw
            // java.sql.Timestamp — handle both rather than assuming one.
            LocalDateTime month = row[0] instanceof Timestamp ts
                    ? ts.toLocalDateTime()
                    : (LocalDateTime) row[0];
            map.put(month.format(MONTH_FORMAT), ((Number) row[1]).longValue());
        }
        return map;
    }

    private List<String> last12MonthKeys() {
        LocalDateTime now = LocalDateTime.now();
        List<String> months = new ArrayList<>();
        for (int i = 11; i >= 0; i--) {
            months.add(now.minusMonths(i).format(MONTH_FORMAT));
        }
        return months;
    }
}
