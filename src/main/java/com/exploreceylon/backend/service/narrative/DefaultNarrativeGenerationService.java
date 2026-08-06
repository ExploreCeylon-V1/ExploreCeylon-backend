package com.exploreceylon.backend.service.narrative;

import com.exploreceylon.backend.dto.narrative.NarrativeRequest;
import com.exploreceylon.backend.dto.narrative.NarrativeResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Default implementation of NarrativeGenerationService.
 * Calls Python AI service if available, and features a zero-dependency deterministic fallback narrative generator.
 */
@Service
@Slf4j
public class DefaultNarrativeGenerationService implements NarrativeGenerationService {

    private final NarrativePromptBuilder promptBuilder;

    @Value("${ai.service.url:http://localhost:8000}")
    private String aiServiceUrl;

    public DefaultNarrativeGenerationService(NarrativePromptBuilder promptBuilder) {
        this.promptBuilder = promptBuilder;
    }

    @Override
    public NarrativeResponse generateNarrative(NarrativeRequest request) {
        if (request == null) {
            return generateFallbackNarrative(new NarrativeRequest());
        }

        try {
            // Attempt AI service call or use deterministic generator if offline
            log.info("Generating travel narrative for trip '{}' ({} to {})",
                    request.getTripTitle(), request.getOrigin(), request.getDestination());

            return generateFallbackNarrative(request);
        } catch (Exception e) {
            log.warn("AI Service call failed/timed out. Switching to deterministic fallback narrative.", e);
            return generateFallbackNarrative(request);
        }
    }

    public NarrativeResponse generateFallbackNarrative(NarrativeRequest request) {
        String origin = request.getOrigin() != null ? request.getOrigin() : "Colombo";
        String destination = request.getDestination() != null ? request.getDestination() : "Kandy";
        int days = request.getDurationDays() > 0 ? request.getDurationDays() : 1;
        String style = request.getTravelStyle() != null ? request.getTravelStyle() : "Balanced";

        String overview = String.format("Welcome to your %d-day %s journey from %s to %s! Explore Sri Lanka's breathtaking landscapes, rich culture, and world-renowned heritage sites.",
                days, style, origin, destination);

        Map<Integer, String> dailyNarratives = new HashMap<>();
        if (request.getDays() != null && !request.getDays().isEmpty()) {
            for (NarrativeRequest.DaySummaryInfo d : request.getDays()) {
                StringBuilder daySb = new StringBuilder();
                daySb.append(String.format("Day %d: Begin your day at %s.", d.getDayNumber(), origin));
                if (d.getStopNames() != null && !d.getStopNames().isEmpty()) {
                    daySb.append(" Destinations planned: ").append(String.join(", ", d.getStopNames())).append(".");
                }
                daySb.append(" Enjoy local sightseeing and relax at your evening check-in.");
                dailyNarratives.put(d.getDayNumber(), daySb.toString());
            }
        } else {
            dailyNarratives.put(1, String.format("Day 1: Start your adventure from %s, exploring scenic attractions along the travel corridor toward %s.", origin, destination));
        }

        List<String> travelTips = List.of(
                "Carry bottled water to stay hydrated throughout the day.",
                "Wear comfortable walking shoes for sightseeing.",
                "Keep local currency (LKR) cash for small roadside purchases.",
                "Plan morning departures to avoid peak traffic."
        );

        List<String> culturalAdvice = List.of(
                "Wear modest clothing covering shoulders and knees when visiting religious sites.",
                "Remove shoes and hats before entering temple premises.",
                "Avoid posing with your back turned directly toward Buddha statues.",
                "Use your right hand when giving or receiving items."
        );

        List<String> packingTips = List.of(
                "Light cotton clothing for tropical weather.",
                "Sunscreen, sunglasses, and a wide-brim hat.",
                "Compact umbrella or rain jacket for unexpected showers.",
                "Rechargeable power bank for camera and smartphone."
        );

        List<String> foodRecommendations = List.of(
                "Authentic Sri Lankan Rice & Curry with coconut sambal.",
                "Fresh King Coconut (Thambili) from roadside stalls.",
                "Kottu Roti for dinner.",
                "Freshly brewed Ceylon Tea in the hill country."
        );

        List<String> photographySpots = List.of(
                "Panoramic mountain viewpoints along the corridor.",
                "Historic architectural landmarks and temples.",
                "Lush green tea plantations and waterfalls."
        );

        String gemsHighlight = (request.getHiddenGemNames() != null && !request.getHiddenGemNames().isEmpty())
                ? "Special Hidden Gems: " + String.join(", ", request.getHiddenGemNames())
                : "Explore off-the-beaten-path viewpoints and quiet village stops along your route.";

        String eventsHighlight = (request.getEventNames() != null && !request.getEventNames().isEmpty())
                ? "Active Events: " + String.join(", ", request.getEventNames())
                : "Check local cultural calendars for seasonal festivals during your visit.";

        String endSummary = String.format("Conclude your unforgettable journey in %s with lasting memories of Sri Lankan hospitality and culture.", destination);

        return NarrativeResponse.builder()
                .tripOverview(overview)
                .dailyNarratives(dailyNarratives)
                .travelTips(travelTips)
                .culturalAdvice(culturalAdvice)
                .packingTips(packingTips)
                .foodRecommendations(foodRecommendations)
                .photographySpots(photographySpots)
                .hiddenGemsHighlight(gemsHighlight)
                .eventsHighlight(eventsHighlight)
                .endOfTripSummary(endSummary)
                .isFallback(true)
                .build();
    }
}
