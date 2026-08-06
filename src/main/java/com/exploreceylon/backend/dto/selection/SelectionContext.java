package com.exploreceylon.backend.dto.selection;

import com.exploreceylon.backend.dto.budget.DayBudget;
import com.exploreceylon.backend.model.Destination;
import com.exploreceylon.backend.service.ItineraryAssemblyService.GeoPoint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Context parameters passed to DestinationSelectionEngine.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SelectionContext {
    private List<Destination> orderedCandidates;
    private List<DayBudget> dayBudgets;
    private GeoPoint origin;
    private GeoPoint destination;
    private String travelStyle;
    private int tripDurationDays;
}
