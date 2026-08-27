package com.exploreceylon.backend.service.corridor;

import com.exploreceylon.backend.model.Destination;
import com.exploreceylon.backend.service.ItineraryAssemblyService.GeoPoint;
import com.exploreceylon.backend.util.HaversineDistanceCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TravelCorridorEngineTest {

    private DefaultTravelCorridorEngine corridorEngine;

    @BeforeEach
    void setUp() {
        corridorEngine = new DefaultTravelCorridorEngine(new HaversineDistanceCalculator());
    }

    @Test
    @DisplayName("Should exclude coastal towns (Panadura, Kalutara, Dehiwala) from inland Colombo -> Nuwara Eliya corridor")
    void testColomboToNuwaraEliya_ExcludesCoastalTowns() {
        // Origin: Colombo (6.9271, 79.8612) -> Destination: Nuwara Eliya (6.9497, 80.7891)
        GeoPoint colombo = new GeoPoint(6.9271, 79.8612);
        GeoPoint nuwaraEliya = new GeoPoint(6.9497, 80.7891);

        Destination kitulgala = Destination.builder()
                .id(1L).name("Kitulgala White Water Rafting")
                .latitude(6.9908).longitude(80.4197) // Inland along the path
                .build();

        Destination dehiwala = Destination.builder()
                .id(2L).name("Dehiwala Zoo")
                .latitude(6.8511).longitude(79.8653) // Coastal south of Colombo
                .build();

        Destination panadura = Destination.builder()
                .id(3L).name("Panadura Beach Park")
                .latitude(6.7106).longitude(79.9074) // Coastal south (~25km from route)
                .build();

        Destination kalutara = Destination.builder()
                .id(4L).name("Kalutara Bodhiya")
                .latitude(6.5854).longitude(79.9607) // Coastal south (~40km from route)
                .build();

        List<Destination> rawCandidates = List.of(kitulgala, dehiwala, panadura, kalutara);

        CorridorContext context = CorridorContext.builder()
                .origin(colombo)
                .destination(nuwaraEliya)
                .widthKm(10.0)
                .maxDetourKm(20.0)
                .corridorEnabled(true)
                .build();

        List<Destination> filtered = corridorEngine.filterCandidates(rawCandidates, context);

        // Kitulgala should be retained; coastal towns Panadura & Kalutara must be excluded
        assertTrue(filtered.stream().anyMatch(d -> d.getName().contains("Kitulgala")), "Kitulgala should be in corridor");
        assertFalse(filtered.stream().anyMatch(d -> d.getName().contains("Panadura")), "Panadura must be excluded");
        assertFalse(filtered.stream().anyMatch(d -> d.getName().contains("Kalutara")), "Kalutara must be excluded");
        assertTrue(filtered.size() < rawCandidates.size(), "Corridor candidate count must be smaller than broad candidate count");
    }

    @Test
    @DisplayName("Should return all candidates when corridor filtering is disabled")
    void testCorridorDisabled() {
        Destination d1 = Destination.builder().id(1L).name("Spot 1").latitude(6.0).longitude(80.0).build();
        Destination d2 = Destination.builder().id(2L).name("Spot 2").latitude(7.0).longitude(81.0).build();

        CorridorContext context = CorridorContext.builder()
                .corridorEnabled(false)
                .build();

        List<Destination> filtered = corridorEngine.filterCandidates(List.of(d1, d2), context);

        assertEquals(2, filtered.size(), "Disabling corridor must return all raw candidates");
    }

    @Test
    @DisplayName("Should retain wide destination catchment destinations while strictly excluding narrow intermediate detours")
    void testDualZoneCorridor_RetainsDestinationZoneAndExcludesIntermediateDetours() {
        GeoPoint colombo = new GeoPoint(6.9271, 79.8612);
        GeoPoint ella = new GeoPoint(6.8667, 81.0466);

        // 1. Departure / start zone (Colombo district)
        Destination gangaramaya = Destination.builder()
                .id(1L).name("Gangaramaya Temple").district("Colombo")
                .latitude(6.9167).longitude(79.8569).build();

        // 2. Intermediate highway on-route (Ratnapura / Balangoda on A4 highway)
        Destination ratnapuraGem = Destination.builder()
                .id(2L).name("Ratnapura Gem Museum").district("Ratnapura")
                .latitude(6.6828).longitude(80.4036).build();

        // 3. Intermediate lateral detour (Sinharaja Rainforest - 30km off highway)
        Destination sinharaja = Destination.builder()
                .id(3L).name("Sinharaja Forest Reserve").district("Ratnapura")
                .latitude(6.4167).longitude(80.4667).build();

        // 4. Destination zone (Ella / Badulla district, ~18km off exact endpoint)
        Destination diyalumaFalls = Destination.builder()
                .id(4L).name("Diyaluma Falls").district("Badulla")
                .latitude(6.7333).longitude(81.0333).build();

        List<Destination> candidates = List.of(gangaramaya, ratnapuraGem, sinharaja, diyalumaFalls);

        CorridorContext context = CorridorContext.builder()
                .origin(colombo)
                .destination(ella)
                .routePath(List.of(colombo, new GeoPoint(6.6828, 80.4036), new GeoPoint(6.75, 80.70), ella))
                .originDistrict("Colombo")
                .destinationDistrict("Badulla")
                .intermediateWidthKm(8.0)
                .destinationZoneWidthKm(30.0)
                .destinationZoneRadiusKm(25.0)
                .maxDetourKm(25.0)
                .corridorEnabled(true)
                .build();

        List<Destination> filtered = corridorEngine.filterCandidates(candidates, context);

        assertFalse(filtered.stream().anyMatch(d -> d.getName().contains("Gangaramaya")), "Start district POI must be excluded for multi-district trip");
        assertTrue(filtered.stream().anyMatch(d -> d.getName().contains("Ratnapura")), "On-route intermediate POI should be retained");
        assertFalse(filtered.stream().anyMatch(d -> d.getName().contains("Sinharaja")), "Far intermediate detour (Sinharaja) must be excluded by narrow 8km ribbon");
        assertTrue(filtered.stream().anyMatch(d -> d.getName().contains("Diyaluma")), "Destination zone POI in Badulla should be retained by wide catchment");
    }

    @Test
    @DisplayName("Single-district trip: retains places in the starting district")
    void testSingleDistrictCorridor_RetainsStartingDistrictPlaces() {
        GeoPoint colombo = new GeoPoint(6.9271, 79.8612);

        Destination gangaramaya = Destination.builder()
                .id(1L).name("Gangaramaya Temple").district("Colombo")
                .latitude(6.9167).longitude(79.8569).build();

        List<Destination> candidates = List.of(gangaramaya);

        CorridorContext context = CorridorContext.builder()
                .origin(colombo)
                .destination(colombo)
                .originDistrict("Colombo")
                .destinationDistrict("Colombo")
                .widthKm(10.0)
                .maxDetourKm(20.0)
                .corridorEnabled(true)
                .build();

        List<Destination> filtered = corridorEngine.filterCandidates(candidates, context);
        assertTrue(filtered.stream().anyMatch(d -> d.getName().contains("Gangaramaya")),
                "Single-district trip must retain starting district places");
    }
}
