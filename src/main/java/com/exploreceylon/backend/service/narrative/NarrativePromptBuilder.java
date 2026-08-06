package com.exploreceylon.backend.service.narrative;

import com.exploreceylon.backend.dto.narrative.NarrativeRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service constructing clean, structured LLM prompts from itinerary DTOs.
 */
@Service
@Slf4j
public class NarrativePromptBuilder {

    public String buildUserPrompt(NarrativeRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Trip Title: %s\n", request.getTripTitle()));
        sb.append(String.format("Route: %s to %s (%d days)\n", request.getOrigin(), request.getDestination(), request.getDurationDays()));
        sb.append(String.format("Travel Style: %s, Budget: %s, Group Size: %d\n\n",
                request.getTravelStyle(), request.getBudgetRange(), request.getGroupSize()));

        if (request.getDays() != null) {
            for (NarrativeRequest.DaySummaryInfo day : request.getDays()) {
                sb.append(String.format("Day %d Stops:\n", day.getDayNumber()));
                if (day.getStopNames() != null) {
                    for (int i = 0; i < day.getStopNames().size(); i++) {
                        String timeStr = (day.getArrivalTimes() != null && i < day.getArrivalTimes().size())
                                ? day.getArrivalTimes().get(i) : "";
                        sb.append(String.format(" - %s at %s\n", day.getStopNames().get(i), timeStr));
                    }
                }
            }
        }

        if (request.getHiddenGemNames() != null && !request.getHiddenGemNames().isEmpty()) {
            sb.append("\nRecommended Hidden Gems: ").append(String.join(", ", request.getHiddenGemNames())).append("\n");
        }

        if (request.getEventNames() != null && !request.getEventNames().isEmpty()) {
            sb.append("Recommended Seasonal Events: ").append(String.join(", ", request.getEventNames())).append("\n");
        }

        sb.append("\nPlease generate a warm, engaging, human travel narrative guide for this itinerary.");
        return sb.toString();
    }
}
