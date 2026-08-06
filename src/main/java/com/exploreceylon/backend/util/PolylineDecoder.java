package com.exploreceylon.backend.util;

import com.exploreceylon.backend.service.ItineraryAssemblyService.GeoPoint;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility for decoding Google Encoded Polyline strings into sequential GeoPoint coordinate lists.
 */
@Slf4j
public class PolylineDecoder {

    /**
     * Decodes an encoded polyline string (5-decimal precision) into a list of GeoPoints.
     *
     * @param encoded Encoded polyline string
     * @return List of GeoPoint coordinates
     */
    public static List<GeoPoint> decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return List.of();
        }

        List<GeoPoint> poly = new ArrayList<>();
        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;

        try {
            while (index < len) {
                int b, shift = 0, result = 0;
                do {
                    b = encoded.charAt(index++) - 63;
                    result |= (b & 0x1f) << shift;
                    shift += 5;
                } while (b >= 0x20);
                int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
                lat += dlat;

                shift = 0;
                result = 0;
                do {
                    b = encoded.charAt(index++) - 63;
                    result |= (b & 0x1f) << shift;
                    shift += 5;
                } while (b >= 0x20);
                int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
                lng += dlng;

                double pLat = (double) lat / 1E5;
                double pLng = (double) lng / 1E5;
                poly.add(new GeoPoint(pLat, pLng));
            }
        } catch (Exception e) {
            log.warn("Failed to decode polyline string: {}", e.getMessage());
        }

        return poly;
    }
}
