package com.exploreceylon.backend.dto.editable;

import com.exploreceylon.backend.dto.matrix.RouteMatrix;
import com.exploreceylon.backend.service.ItineraryAssemblyService.PlannedDay;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Set;

/**
 * Context DTO passed to EditableTripEngine for localized re-optimization.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EditContext {
    private List<PlannedDay> originalDays;
    private RouteMatrix routeMatrix;
    private List<EditOperation> operations;
    private Set<Long> lockedStopIds;
}
