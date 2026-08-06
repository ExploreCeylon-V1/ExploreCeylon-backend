package com.exploreceylon.backend.dto.regeneration;

import com.exploreceylon.backend.dto.cost.TripCostEstimate;
import com.exploreceylon.backend.dto.narrative.NarrativeResponse;
import com.exploreceylon.backend.service.ItineraryAssemblyService.PlannedDay;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Result DTO returned after localized trip regeneration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripRegenerationResult {
    private boolean success;
    private String message;
    private List<PlannedDay> updatedDays;
    private List<AffectedDay> affectedDays;
    private NarrativeResponse updatedNarrative;
    private TripCostEstimate updatedCostEstimate;
    private RegenerationStatistics statistics;
}
