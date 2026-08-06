package com.exploreceylon.backend.service.selection;

import com.exploreceylon.backend.dto.selection.SelectedStop;
import com.exploreceylon.backend.dto.selection.SelectionContext;
import com.exploreceylon.backend.dto.selection.SelectionStatistics;
import com.exploreceylon.backend.service.ItineraryAssemblyService.TripDay;

import java.util.List;

/**
 * Strategy interface for allocating and scheduling candidate destinations into daily trip itineraries.
 */
public interface DestinationSelectionEngine {

    /**
     * Selects and schedules candidate destinations into trip days adhering to travel budgets,
     * category diversity limits, timeline constraints, and district progression rules.
     *
     * @param context SelectionContext containing ordered candidates, day budgets, and route endpoints.
     * @return List of assembled TripDay instances with scheduled stops.
     */
    List<TripDay> selectAndScheduleDestinations(SelectionContext context);

    /**
     * Evaluates selection statistics and quality score for an assembled day itinerary.
     *
     * @param day Single trip day.
     * @return SelectionStatistics DTO.
     */
    SelectionStatistics evaluateDaySelection(TripDay day);
}
