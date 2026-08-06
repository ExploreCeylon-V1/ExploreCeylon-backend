package com.exploreceylon.backend.dto.recommendation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing a recommended hidden gem or seasonal event inserted into an itinerary.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendedGem {
    private Long id;
    private String name;
    private String type;                   // "HIDDEN_GEM" or "EVENT"
    private String category;
    private Double latitude;
    private Double longitude;
    private Double rating;
    private Integer reviewCount;
    private int insertionIndex;
    private int estimatedVisitMinutes;
    private double recommendationScore;
    private String rationale;
}
