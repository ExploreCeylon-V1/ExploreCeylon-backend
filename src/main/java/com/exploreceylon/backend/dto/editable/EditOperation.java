package com.exploreceylon.backend.dto.editable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO capturing a single edit instruction.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EditOperation {
    private EditOperationType type;
    private Integer dayNumber;
    private Long stopId;
    private Long targetStopId;               // Used for REPLACE_STOP or MOVE_STOP target
    private Integer newStartMinutes;          // For CHANGE_START_TIME
    private Integer newVisitDurationMinutes;  // For CHANGE_VISIT_DURATION
    private String newTravelStyle;           // For CHANGE_TRAVEL_STYLE
    private String newBudgetRange;           // For CHANGE_BUDGET
    private Boolean locked;                  // For LOCK_STOP / UNLOCK_STOP
}
