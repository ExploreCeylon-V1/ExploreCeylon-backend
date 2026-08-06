package com.exploreceylon.backend.service.budget;

import com.exploreceylon.backend.dto.budget.DayBudget;
import com.exploreceylon.backend.dto.budget.DayBudgetContext;

import java.util.List;

/**
 * Reusable strategy interface for calculating daily time, travel, and meal budgets for trip itineraries.
 */
public interface DayBudgetEngine {

    /**
     * Calculates realistic daily budgets for all days in a trip itinerary.
     *
     * @param context DayBudgetContext containing trip duration, travel style, and OSRM route metrics.
     * @return List of DayBudget objects for each day of the trip.
     */
    List<DayBudget> calculateTripDayBudgets(DayBudgetContext context);

    /**
     * Calculates realistic daily budget for a single specified day.
     *
     * @param dayNumber Day index (1-based).
     * @param context   DayBudgetContext containing trip duration and route metrics.
     * @return DayBudget object for the specified day.
     */
    DayBudget calculateSingleDayBudget(int dayNumber, DayBudgetContext context);
}
