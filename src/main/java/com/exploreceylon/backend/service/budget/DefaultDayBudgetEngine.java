package com.exploreceylon.backend.service.budget;

import com.exploreceylon.backend.dto.budget.DayBudget;
import com.exploreceylon.backend.dto.budget.DayBudgetContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Default implementation of DayBudgetEngine.
 * Computes realistic daily travel, sightseeing, meal, and buffer time budgets
 * for every day of an itinerary based on travel style and OSRM route metrics.
 */
@Service
@Slf4j
public class DefaultDayBudgetEngine implements DayBudgetEngine {

    private final VisitDurationEstimator visitDurationEstimator;

    @Value("${planner.meals.lunch-minutes:60}")
    private int lunchMinutes = 60;

    @Value("${planner.meals.tea-minutes:20}")
    private int teaMinutes = 20;

    @Value("${planner.meals.dinner-minutes:60}")
    private int dinnerMinutes = 60;

    @Value("${planner.buffer.delay-minutes:45}")
    private int delayBufferMinutes = 45;

    @Value("${planner.hotel.checkin-minutes:30}")
    private int checkinMinutes = 30;

    @Value("${planner.hotel.checkout-minutes:30}")
    private int checkoutMinutes = 30;

    // Relaxed style (Max 5h sightseeing, 4h driving)
    @Value("${planner.travel-style.relaxed.sightseeing-minutes:300}")
    private int relaxedSightseeingMinutes = 300;

    @Value("${planner.travel-style.relaxed.driving-minutes:240}")
    private int relaxedDrivingMinutes = 240;

    // Balanced style (Max 6.5h sightseeing, 5h driving)
    @Value("${planner.travel-style.balanced.sightseeing-minutes:390}")
    private int balancedSightseeingMinutes = 390;

    @Value("${planner.travel-style.balanced.driving-minutes:300}")
    private int balancedDrivingMinutes = 300;

    // Fast-paced style (Max 8h sightseeing, 7h driving)
    @Value("${planner.travel-style.fast-paced.sightseeing-minutes:480}")
    private int fastPacedSightseeingMinutes = 480;

    @Value("${planner.travel-style.fast-paced.driving-minutes:420}")
    private int fastPacedDrivingMinutes = 420;

    public DefaultDayBudgetEngine(VisitDurationEstimator visitDurationEstimator) {
        this.visitDurationEstimator = visitDurationEstimator;
    }

    @Override
    public List<DayBudget> calculateTripDayBudgets(DayBudgetContext context) {
        if (context == null || context.getTripDurationDays() <= 0) {
            return List.of();
        }

        List<DayBudget> dayBudgets = new ArrayList<>();
        for (int day = 1; day <= context.getTripDurationDays(); day++) {
            dayBudgets.add(calculateSingleDayBudget(day, context));
        }

        log.info("DayBudgetEngine calculated {} daily time budgets for travel style: {}",
                dayBudgets.size(), context.getTravelStyle());
        return dayBudgets;
    }

    @Override
    public DayBudget calculateSingleDayBudget(int dayNumber, DayBudgetContext context) {
        int durationDays = (context != null && context.getTripDurationDays() > 0) ? context.getTripDurationDays() : 1;
        String styleStr = (context != null && context.getTravelStyle() != null) ? context.getTravelStyle() : "BALANCED";

        int maxSightseeingMinutes = getSightseeingCap(styleStr);
        int maxDrivingMinutes = getDrivingCap(styleStr);

        int reservedMealMinutes = lunchMinutes + teaMinutes + dinnerMinutes; // 140 min
        int reservedBufferMinutes = delayBufferMinutes; // 45 min
        int reservedHotelMinutes = checkinMinutes + checkoutMinutes; // 60 min

        double totalDistanceKm = context != null ? context.getTotalRouteDistanceKm() : 0.0;
        int totalRouteMinutes = context != null ? context.getTotalRouteDurationMinutes() : 0;

        double drivingDistanceTargetKm = totalDistanceKm / durationDays;
        int targetRouteDrivingMinutes = totalRouteMinutes > 0 ? totalRouteMinutes / durationDays : (int) Math.round((drivingDistanceTargetKm / 40.0) * 60.0);
        int availableDrivingMinutes = Math.min(maxDrivingMinutes, Math.max(30, targetRouteDrivingMinutes));

        int totalAllocatedMinutes = maxSightseeingMinutes + availableDrivingMinutes + reservedMealMinutes + reservedBufferMinutes + reservedHotelMinutes;
        int remainingMinutes = Math.max(0, 1440 - totalAllocatedMinutes);

        int maximumVisitCount = (int) Math.floor((double) maxSightseeingMinutes / 60.0);

        return DayBudget.builder()
                .dayNumber(dayNumber)
                .availableDrivingMinutes(availableDrivingMinutes)
                .availableSightseeingMinutes(maxSightseeingMinutes)
                .reservedMealMinutes(reservedMealMinutes)
                .reservedBufferMinutes(reservedBufferMinutes)
                .drivingDistanceTargetKm(Math.round(drivingDistanceTargetKm * 100.0) / 100.0)
                .remainingMinutes(remainingMinutes)
                .maximumVisitCount(maximumVisitCount)
                .build();
    }

    private int getSightseeingCap(String styleStr) {
        String s = styleStr.trim().toUpperCase(Locale.ROOT);
        if (s.contains("RELAXED") || s.contains("LEISURE")) {
            return relaxedSightseeingMinutes;
        } else if (s.contains("FAST") || s.contains("ADVENTURE") || s.contains("INTENSE")) {
            return fastPacedSightseeingMinutes;
        }
        return balancedSightseeingMinutes;
    }

    private int getDrivingCap(String styleStr) {
        String s = styleStr.trim().toUpperCase(Locale.ROOT);
        if (s.contains("RELAXED") || s.contains("LEISURE")) {
            return relaxedDrivingMinutes;
        } else if (s.contains("FAST") || s.contains("ADVENTURE") || s.contains("INTENSE")) {
            return fastPacedDrivingMinutes;
        }
        return balancedDrivingMinutes;
    }
}
