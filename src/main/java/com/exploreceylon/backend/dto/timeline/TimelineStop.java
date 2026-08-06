package com.exploreceylon.backend.dto.timeline;

import com.exploreceylon.backend.model.Destination;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing a fully scheduled timeline stop with clock times, waiting durations, and breaks.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimelineStop {
    private Destination destination;
    private int sequenceIndex;
    private String arrivalTime;           // e.g. "08:45 AM"
    private int waitingMinutes;           // Waiting period if arriving before opening
    private String visitStartTime;         // e.g. "09:00 AM"
    private String visitEndTime;           // e.g. "10:00 AM"
    private String departureTime;         // e.g. "10:08 AM" (includes walking buffer)
    private int travelDurationMinutesFromPrevious;
    private double travelDistanceKmFromPrevious;
    private String category;
    private String breakType;             // "LUNCH", "TEA", "DINNER", "DRIVING_REST", "NONE"
}
