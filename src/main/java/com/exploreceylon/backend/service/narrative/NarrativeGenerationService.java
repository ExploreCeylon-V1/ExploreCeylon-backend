package com.exploreceylon.backend.service.narrative;

import com.exploreceylon.backend.dto.narrative.NarrativeRequest;
import com.exploreceylon.backend.dto.narrative.NarrativeResponse;

/**
 * Strategy interface for generating AI-powered or deterministic itinerary travel narratives.
 */
public interface NarrativeGenerationService {

    /**
     * Generates a complete travel narrative for the specified trip itinerary.
     * Automatically uses LLM service if available with a retry, falling back to deterministic generation.
     *
     * @param request NarrativeRequest containing trip metadata and daily stops.
     * @return NarrativeResponse containing overview, daily guides, travel tips, and cultural advice.
     */
    NarrativeResponse generateNarrative(NarrativeRequest request);
}
