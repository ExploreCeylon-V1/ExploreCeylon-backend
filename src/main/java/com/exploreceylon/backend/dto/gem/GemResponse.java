package com.exploreceylon.backend.dto.gem;

import com.exploreceylon.backend.model.HiddenGem;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class GemResponse {
    private Long id;
    private String title;
    private String description;
    private HiddenGem.GemCategory category;
    private String district;
    private Double latitude;
    private Double longitude;
    private String howToGetThere;
    private String bestTime;
    private String tips;
    private List<String> imageUrls;
    private Boolean approved;
    private Double rating;
    private Integer reviewCount;
    private LocalDateTime createdAt;
    private String seasonMonths;
    private String travelStyleTags;
    private com.exploreceylon.backend.model.BudgetLevel budgetLevel;
    private Double entryFeeUsd;
    private Integer visitDurationMinutes;
}