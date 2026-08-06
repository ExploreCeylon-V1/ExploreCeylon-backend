package com.exploreceylon.backend.dto.matrix;

import com.exploreceylon.backend.service.ItineraryAssemblyService.GeoPoint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Immutable DTO encapsulating the N x N distance and duration matrix for a trip's locations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteMatrix {
    private double[][] distancesKm;
    private double[][] durationsMinutes;
    private List<GeoPoint> locations;
    private Map<String, Integer> locationIndexMap;
    private String providerUsed;
    private String cacheKey;
    private MatrixStatistics statistics;

    public RouteMatrixEntry getEntry(GeoPoint from, GeoPoint to) {
        if (from == null || to == null || locationIndexMap == null) {
            return new RouteMatrixEntry(0.0, 0.0, providerUsed);
        }
        String keyFrom = from.lat() + "," + from.lng();
        String keyTo = to.lat() + "," + to.lng();
        Integer idxFrom = locationIndexMap.get(keyFrom);
        Integer idxTo = locationIndexMap.get(keyTo);

        if (idxFrom != null && idxTo != null && idxFrom < distancesKm.length && idxTo < distancesKm[0].length) {
            return new RouteMatrixEntry(distancesKm[idxFrom][idxTo], durationsMinutes[idxFrom][idxTo], providerUsed);
        }

        return new RouteMatrixEntry(0.0, 0.0, providerUsed);
    }
}
