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

    // ── Narrative-only generation ──────────────────────────
    // The backend (ItineraryAssemblyService) now decides the full
    // day/stop structure — corridor, order, day assignment, referenceId —
    // deterministically before this is ever called. The AI service's only
    // remaining job is to write engaging descriptions/tips/title for the
    // fixed structure passed in `body["days"]`; it must not reorder, add
    // or remove stops. See ExploreCeylon-ai-service prompt_builder.py
    // build_narrative_prompt() / routers/itinerary.py "/ai/itinerary/narrate".
    public Mono<JsonNode> generateNarrative(Map<String, Object> body) {
        log.info("Calling AI service for narrative-only enrichment");

        return aiWebClient.post()
                .uri("/ai/itinerary/narrate")
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

    // Per-day budget is now computed in Java from real chosen stops
    // (ItineraryAssemblyService.computeDayCost) instead of this flat
    // rate-table call — see Phase 5 notes there. The Python
    // /ai/budget-estimate endpoint still exists for any other generic
    // estimate use but is no longer part of the trip-generation flow.

    // ── Health Check ───────────────────────────────────────
    public Mono<JsonNode> healthCheck() {
        return aiWebClient.get()
                .uri("/ai/health")
                .retrieve()
                .bodyToMono(JsonNode.class);
    }
}