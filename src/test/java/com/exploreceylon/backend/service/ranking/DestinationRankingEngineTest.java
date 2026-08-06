package com.exploreceylon.backend.service.ranking;

import com.exploreceylon.backend.model.BudgetLevel;
import com.exploreceylon.backend.model.Destination;
import com.exploreceylon.backend.service.ItineraryAssemblyService.GeoPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DestinationRankingEngineTest {

    private DefaultDestinationRankingEngine rankingEngine;

    @BeforeEach
    void setUp() {
        rankingEngine = new DefaultDestinationRankingEngine(new com.exploreceylon.backend.util.HaversineDistanceCalculator());
    }

    @Test
    @DisplayName("Should score higher for featured and UNESCO destinations matching travel style")
    void testCalculateScore_FeaturedAndUnesco() {
        Destination unescoDest = Destination.builder()
                .id(1L)
                .name("Sigiriya Rock Fortress")
                .category(Destination.DestinationCategory.CULTURAL)
                .travelStyleTags("CULTURAL,HERITAGE,ADVENTURE")
                .bestMonths("january,february,march,april,may,june,july,august,september,october,november,december")
                .rating(4.9)
                .reviewCount(500)
                .featured(true)
                .unescoStatus("World Heritage Site")
                .latitude(7.9570)
                .longitude(80.7603)
                .build();

        Destination basicDest = Destination.builder()
                .id(2L)
                .name("Local Small Park")
                .category(Destination.DestinationCategory.CITY)
                .travelStyleTags("RELAXATION")
                .bestMonths("january")
                .rating(3.2)
                .reviewCount(10)
                .featured(false)
                .unescoStatus(null)
                .latitude(7.9500)
                .longitude(80.7500)
                .build();

        RankingContext context = RankingContext.builder()
                .origin(new GeoPoint(7.9570, 80.7603))
                .currentPosition(new GeoPoint(7.9570, 80.7603))
                .destination(new GeoPoint(7.9570, 80.7603))
                .travelStyles(List.of("CULTURAL", "ADVENTURE"))
                .tripMonths(Set.of("january", "february"))
                .budgetLevel(BudgetLevel.MID_RANGE)
                .build();

        double unescoScore = rankingEngine.calculateScore(unescoDest, context);
        double basicScore = rankingEngine.calculateScore(basicDest, context);

        assertTrue(unescoScore > basicScore, "UNESCO featured destination must score higher than a basic park");
        assertTrue(unescoScore > 75.0, "High-quality destination should achieve a score > 75");
    }

    @Test
    @DisplayName("Should correctly rank candidate destinations in descending order of composite score")
    void testRankDestinations() {
        Destination d1 = Destination.builder()
                .id(1L).name("Spot 1").rating(3.0).reviewCount(5).featured(false)
                .build();
        Destination d2 = Destination.builder()
                .id(2L).name("Spot 2").rating(4.8).reviewCount(250).featured(true)
                .build();
        Destination d3 = Destination.builder()
                .id(3L).name("Spot 3").rating(4.0).reviewCount(50).featured(false)
                .build();

        RankingContext context = RankingContext.builder().build();

        List<Destination> ranked = rankingEngine.rankDestinations(List.of(d1, d2, d3), context);

        assertEquals(3, ranked.size());
        assertEquals("Spot 2", ranked.get(0).getName(), "Spot 2 should be ranked first");
        assertEquals("Spot 3", ranked.get(1).getName(), "Spot 3 should be ranked second");
        assertEquals("Spot 1", ranked.get(2).getName(), "Spot 1 should be ranked last");
    }

    @Test
    @DisplayName("Should produce detailed DestinationScore breakdown with component values")
    void testExplainScore() {
        Destination unescoDest = Destination.builder()
                .id(10L)
                .name("Galle Fort")
                .category(Destination.DestinationCategory.HERITAGE)
                .travelStyleTags("CULTURAL,HERITAGE")
                .bestMonths("january,february,march")
                .rating(4.8)
                .reviewCount(300)
                .featured(true)
                .unescoStatus("World Heritage Site")
                .latitude(6.0535)
                .longitude(80.2210)
                .build();

        RankingContext context = RankingContext.builder()
                .currentPosition(new GeoPoint(6.0535, 80.2210))
                .travelStyles(List.of("CULTURAL"))
                .tripMonths(Set.of("january"))
                .build();

        DestinationScore explanation = rankingEngine.explainScore(unescoDest, context);

        assertNotNull(explanation);
        assertEquals(10L, explanation.getDestinationId());
        assertEquals("Galle Fort", explanation.getDestinationName());
        assertTrue(explanation.getFinalScore() > 80.0);
        assertTrue(explanation.getRatingScore() > 20.0);
        assertTrue(explanation.getPopularityScore() > 10.0);
        assertTrue(explanation.getStyleScore() > 20.0);
        assertTrue(explanation.getSeasonScore() > 5.0);
        assertTrue(explanation.getBonusScore() == 10.0);
        assertTrue(explanation.isSeasonMatch());
        assertTrue(explanation.getMatchingStyles().contains("CULTURAL"));
    }

    @Test
    @DisplayName("Should handle null destination and context gracefully without throwing exception")
    void testNullHandling() {
        assertDoesNotThrow(() -> {
            double score = rankingEngine.calculateScore(null, null);
            assertEquals(0.0, score);
        });

        assertDoesNotThrow(() -> {
            List<Destination> ranked = rankingEngine.rankDestinations(null, null);
            assertTrue(ranked.isEmpty());
        });
    }
}
