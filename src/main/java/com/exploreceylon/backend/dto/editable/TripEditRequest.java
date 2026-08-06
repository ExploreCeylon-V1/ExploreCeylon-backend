package com.exploreceylon.backend.dto.editable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for localized itinerary edits.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripEditRequest {
    private Long tripId;
    private List<EditOperation> operations;
}
