package com.exploreceylon.backend.dto.selection;

import com.exploreceylon.backend.model.Destination;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing an allocated and scheduled destination stop within a trip day.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SelectedStop {
    private Destination destination;
    private int sequenceIndex;
    private String arrivalTime;       // e.g. "09:10 AM"
    private String departureTime;     // e.g. "10:00 AM"
    private int visitDurationMinutes;
    private int travelDurationMinutesFromPrevious;
    private double travelDistanceKmFromPrevious;
    private String category;
}
