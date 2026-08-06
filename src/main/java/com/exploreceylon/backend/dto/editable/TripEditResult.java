package com.exploreceylon.backend.dto.editable;

import com.exploreceylon.backend.service.ItineraryAssemblyService.PlannedDay;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Result DTO containing modified planned days, edit statistics, and lock state mappings.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripEditResult {
    private boolean success;
    private String message;
    private List<PlannedDay> updatedDays;
    private List<Integer> affectedDayNumbers;
    private Map<Long, Boolean> lockStates;
    private EditStatistics statistics;
}
