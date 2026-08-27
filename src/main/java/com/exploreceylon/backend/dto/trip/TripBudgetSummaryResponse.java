package com.exploreceylon.backend.dto.trip;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripBudgetSummaryResponse {
    private Double totalBudget;
    private Double totalSpent;
    private Double remaining;
    private Double usedPercentage;
    private String currency;
    private String status; // ON_TRACK, WARNING, OVER_BUDGET
    private Map<String, Double> categoryBudgets;
    private Map<String, Double> categorySpent;
    private List<PublicBudgetItemResponse> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PublicBudgetItemResponse {
        private Long id;
        private String category;
        private String title;
        private Double amount;
        private String currency;
        private LocalDate date;
        private Boolean autoAdded;
        private String notes;
    }
}
