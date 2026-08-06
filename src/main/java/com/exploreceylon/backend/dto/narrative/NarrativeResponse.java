package com.exploreceylon.backend.dto.narrative;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * DTO returned containing generated narrative sections.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NarrativeResponse {
    private String tripOverview;
    private Map<Integer, String> dailyNarratives; // Day Number -> Daily Narrative Text
    private List<String> travelTips;
    private List<String> culturalAdvice;
    private List<String> packingTips;
    private List<String> foodRecommendations;
    private List<String> photographySpots;
    private String hiddenGemsHighlight;
    private String eventsHighlight;
    private String endOfTripSummary;
    private boolean isFallback;
}
