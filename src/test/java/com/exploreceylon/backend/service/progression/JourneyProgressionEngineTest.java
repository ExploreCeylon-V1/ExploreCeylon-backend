package com.exploreceylon.backend.service.progression;

import com.exploreceylon.backend.dto.progression.JourneyProgress;
import com.exploreceylon.backend.model.Destination;
import com.exploreceylon.backend.service.ItineraryAssemblyService.GeoPoint;
import com.exploreceylon.backend.util.HaversineDistanceCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JourneyProgressionEngineTest {

    private DefaultJourneyProgressionEngine progressionEngine;

    @BeforeEach
    void setUp() {
        progressionEngine = new DefaultJourneyProgressionEngine(new HaversineDistanceCalculator());
    }

    @Test
    @DisplayName("Should correctly order stops forward along Colombo -> Kandy -> Nuwara Eliya route")
    void testOrderCandidatesByProgress_ColomboToNuwaraEliya() {
        GeoPoint colombo = new GeoPoint(6.9271, 79.8612);
        GeoPoint nuwaraEliya = new GeoPoint(6.9497, 80.7891);

        Destination kandy = Destination.builder()
                .id(1L).name("Kandy Temple of Tooth")
                .latitude(7.2906).longitude(80.6337)
                .build();

        Destination gampaha = Destination.builder()
                .id(2L).name("Gampaha Botanical Garden")
                .latitude(7.0873).longitude(79.9925)
                .build();

        Destination kitulgala = Destination.builder()
                .id(3L).name("Kitulgala White Water Rafting")
                .latitude(6.9908).longitude(80.4197)
                .build();

        List<Destination> unordered = List.of(kandy, kitulgala, gampaha);

        ProgressionContext context = ProgressionContext.builder()
                .origin(colombo)
                .destination(nuwaraEliya)
                .minimumForwardDistanceKm(2.0)
                .allowBacktracking(false)
                .progressionEnabled(true)
                .build();

        List<Destination> ordered = progressionEngine.orderCandidatesByProgress(unordered, context);

        assertEquals(3, ordered.size());
        assertEquals("Gampaha Botanical Garden", ordered.get(0).getName(), "Gampaha should be first stop out of Colombo");
        assertEquals("Kitulgala White Water Rafting", ordered.get(1).getName(), "Kitulgala should be middle stop");
        assertEquals("Kandy Temple of Tooth", ordered.get(2).getName(), "Kandy should be final stop before Nuwara Eliya");
    }

    @Test
    @DisplayName("Should compute detailed JourneyProgress DTO with progressDistanceKm and remainingDistanceKm")
    void testCalculateProgress() {
        GeoPoint colombo = new GeoPoint(6.9271, 79.8612);
        GeoPoint nuwaraEliya = new GeoPoint(6.9497, 80.7891);

        Destination kandy = Destination.builder()
                .id(10L).name("Kandy")
                .latitude(7.2906).longitude(80.6337)
                .build();

        ProgressionContext context = ProgressionContext.builder()
                .origin(colombo)
                .destination(nuwaraEliya)
                .build();

        JourneyProgress progress = progressionEngine.calculateProgress(kandy, context);

        assertNotNull(progress);
        assertEquals(10L, progress.getDestinationId());
        assertTrue(progress.getProgressDistanceKm() > 80.0, "Progress distance to Kandy from Colombo should be >80km");
        assertTrue(progress.getRemainingDistanceKm() >= 0.0);
    }
}
