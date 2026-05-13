package com.exploreceylon.backend.dto.budget;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

@Data
@Builder
public class BudgetSummaryResponse {
    private Long budgetId;
    private Long tripId;
    private String tripTitle;
    private Double totalBudget;
    private Double totalSpent;
    private Double remaining;
    private Double usedPercentage;
    private String currency;
    private String status; // ON_TRACK, WARNING, OVER_BUDGET
    private Map<String, Double> categoryBreakdown;
}
