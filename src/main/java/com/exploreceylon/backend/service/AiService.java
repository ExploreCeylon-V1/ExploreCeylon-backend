package com.exploreceylon.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class AiService {

    private final WebClient aiWebClient;

    @Value("${ai.service.url}")
    private String aiServiceUrl;

    public AiService(@Qualifier("aiWebClient") WebClient aiWebClient) {
        this.aiWebClient = aiWebClient;
    }

    // ── Generate Itinerary ─────────────────────────────────
    public Mono<JsonNode> generateItinerary(
            String startDate, String endDate,
            String travelStyle, String budgetRange,
            Integer groupSize, List<String> regions,
            List<String> interests, String startingPoint,
            String specialNotes) {

        Map<String, Object> body = new HashMap<>();
        body.put("start_date",     startDate);
        body.put("end_date",       endDate);
        body.put("travel_style",   travelStyle);
        body.put("budget_range",   budgetRange);
        body.put("group_size",     groupSize);
        body.put("regions",        regions);
        body.put("interests",      interests);
        body.put("starting_point", startingPoint);
        if (specialNotes != null)
            body.put("special_notes", specialNotes);

        log.info("Calling AI service for itinerary generation");

        return aiWebClient.post()
                .uri("/ai/itinerary/generate")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .doOnError(e -> log.error(
                        "AI service error: {}", e.getMessage()));
    }

    // ── Monsoon Check ──────────────────────────────────────
    public Mono<JsonNode> checkMonsoon(
            List<String> regions,
            String startDate, String endDate) {

        Map<String, Object> body = new HashMap<>();
        body.put("regions",     regions);
        body.put("start_date",  startDate);
        body.put("end_date",    endDate);

        return aiWebClient.post()
                .uri("/ai/monsoon-check")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class);
    }

    // ── Budget Estimate ────────────────────────────────────
    public Mono<JsonNode> estimateBudget(
            Integer days, String budgetRange,
            Integer groupSize, List<String> regions) {

        Map<String, Object> body = new HashMap<>();
        body.put("duration_days",  days);
        body.put("budget_range",   budgetRange);
        body.put("group_size",     groupSize);
        body.put("regions",        regions);

        return aiWebClient.post()
                .uri("/ai/budget-estimate")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class);
    }

    // ── Health Check ───────────────────────────────────────
    public Mono<JsonNode> healthCheck() {
        return aiWebClient.get()
                .uri("/ai/health")
                .retrieve()
                .bodyToMono(JsonNode.class);
    }
}