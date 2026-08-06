package com.exploreceylon.backend.dto.timeline;

import com.exploreceylon.backend.dto.budget.DayBudget;
import com.exploreceylon.backend.dto.matrix.RouteMatrix;
import com.exploreceylon.backend.service.ItineraryAssemblyService.GeoPoint;
import com.exploreceylon.backend.service.ItineraryAssemblyService.TripDay;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Context DTO passed to AttractionScheduleEngine to format and optimize daily timelines.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimelineContext {
    private TripDay tripDay;
    private RouteMatrix routeMatrix;
    private DayBudget dayBudget;
    private int dayStartMinutes;         // Minutes from midnight (e.g. 480 = 08:00 AM)
    private GeoPoint originPoint;
}
