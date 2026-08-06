package com.exploreceylon.backend.dto.budget;

import com.exploreceylon.backend.service.ItineraryAssemblyService.GeoPoint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Context DTO containing trip parameters and OSRM route metrics required
 * to compute daily time budgets.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DayBudgetContext {
    private int tripDurationDays;
    private String travelStyle;
    private double totalRouteDistanceKm;
    private int totalRouteDurationMinutes;
    private GeoPoint origin;
    private GeoPoint destination;
}
