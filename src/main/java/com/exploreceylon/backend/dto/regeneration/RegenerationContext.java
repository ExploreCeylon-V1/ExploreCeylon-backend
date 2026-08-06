package com.exploreceylon.backend.dto.regeneration;

import com.exploreceylon.backend.dto.matrix.RouteMatrix;
import com.exploreceylon.backend.model.Event;
import com.exploreceylon.backend.model.HiddenGem;
import com.exploreceylon.backend.service.ItineraryAssemblyService.PlannedDay;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Context DTO containing original itinerary data for localized trip regeneration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegenerationContext {
    private List<PlannedDay> originalDays;
    private RouteMatrix routeMatrix;
    private TripRegenerationRequest request;
    private List<HiddenGem> candidateGems;
    private List<Event> candidateEvents;
}
