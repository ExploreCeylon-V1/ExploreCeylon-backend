package com.exploreceylon.backend.dto.matrix;

import com.exploreceylon.backend.service.ItineraryAssemblyService.GeoPoint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Context DTO passed to RouteMatrixEngine to request a matrix computation for a set of points.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteMatrixContext {
    private List<GeoPoint> locations;
    private String profile;
    private boolean useCache;
}
