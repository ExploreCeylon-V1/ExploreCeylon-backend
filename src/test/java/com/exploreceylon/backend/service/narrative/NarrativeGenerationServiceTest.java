package com.exploreceylon.backend.service.narrative;

import com.exploreceylon.backend.dto.narrative.NarrativeRequest;
import com.exploreceylon.backend.dto.narrative.NarrativeResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NarrativeGenerationServiceTest {

    private NarrativePromptBuilder promptBuilder;
    private DefaultNarrativeGenerationService narrativeService;

    @BeforeEach
    void setUp() {
        promptBuilder = new NarrativePromptBuilder();
        narrativeService = new DefaultNarrativeGenerationService(promptBuilder);
    }

    @Test
    @DisplayName("Should build structured prompt from NarrativeRequest DTO")
    void testPromptBuilder() {
        NarrativeRequest request = NarrativeRequest.builder()
                .tripTitle("Highland Cultural Explorer")
                .origin("Colombo")
                .destination("Kandy")
                .durationDays(2)
                .travelStyle("Balanced")
                .budgetRange("MID_RANGE")
                .groupSize(2)
                .days(List.of(
                        NarrativeRequest.DaySummaryInfo.builder()
                                .dayNumber(1)
                                .stopNames(List.of("Pinnawala", "Kandy Temple"))
                                .arrivalTimes(List.of("09:00 AM", "02:00 PM"))
                                .build()
                ))
                .hiddenGemNames(List.of("Bopath Ella"))
                .eventNames(List.of("Esala Perahera"))
                .build();

        String prompt = promptBuilder.buildUserPrompt(request);
        assertNotNull(prompt);
        assertTrue(prompt.contains("Colombo to Kandy"));
        assertTrue(prompt.contains("Pinnawala"));
        assertTrue(prompt.contains("Bopath Ella"));
    }

    @Test
    @DisplayName("Should generate complete fallback narrative sections when AI service is offline")
    void testFallbackNarrativeGeneration() {
        NarrativeRequest request = NarrativeRequest.builder()
                .tripTitle("Test Trip")
                .origin("Colombo")
                .destination("Nuwara Eliya")
                .durationDays(3)
                .travelStyle("Cultural")
                .hiddenGemNames(List.of("Dunhinda Falls"))
                .build();

        NarrativeResponse response = narrativeService.generateNarrative(request);

        assertNotNull(response);
        assertTrue(response.isFallback(), "Should indicate fallback mode");
        assertNotNull(response.getTripOverview());
        assertFalse(response.getDailyNarratives().isEmpty());
        assertFalse(response.getTravelTips().isEmpty());
        assertFalse(response.getCulturalAdvice().isEmpty());
        assertFalse(response.getPackingTips().isEmpty());
        assertFalse(response.getFoodRecommendations().isEmpty());
        assertFalse(response.getPhotographySpots().isEmpty());
        assertNotNull(response.getHiddenGemsHighlight());
        assertNotNull(response.getEndOfTripSummary());
    }
}
