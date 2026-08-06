package com.exploreceylon.backend.dto.regeneration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Set;

/**
 * Request DTO for localized trip regeneration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripRegenerationRequest {
    private Long tripId;
    private String regenerationType;         // "DESTINATION_REMOVED", "DESTINATION_ADDED", "DESTINATION_REPLACED", "STYLE_CHANGED", "BUDGET_CHANGED", "START_TIME_CHANGED", "DAY_LOCKED"
    private Integer targetDayNumber;
    private Long targetStopId;
    private Long newStopId;
    private String newTravelStyle;
    private String newBudgetRange;
    private Integer newGroupSize;
    private Set<Integer> lockedDayNumbers;
}
