package com.exploreceylon.backend.dto.recommendation;

import com.exploreceylon.backend.dto.budget.DayBudget;
import com.exploreceylon.backend.dto.matrix.RouteMatrix;
import com.exploreceylon.backend.dto.timeline.TimelineStop;
import com.exploreceylon.backend.model.Event;
import com.exploreceylon.backend.model.HiddenGem;
import com.exploreceylon.backend.service.ItineraryAssemblyService.TripDay;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Context DTO passed to GemRecommendationEngine to evaluate gap insertion candidates.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GemRecommendationContext {
    private TripDay tripDay;
    private List<TimelineStop> scheduledStops;
    private RouteMatrix routeMatrix;
    private DayBudget dayBudget;
    private String travelStyle;
    private List<HiddenGem> candidateGems;
    private List<Event> candidateEvents;
    private LocalDate currentDate;
}
