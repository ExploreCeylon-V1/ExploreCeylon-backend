package com.exploreceylon.backend.dto.budget;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class BudgetResponse {
    private Long id;
    private Long tripId;
    private String tripTitle;
    private Double totalBudget;
    private String currency;
    private List<BudgetItemResponse> items;
    private Map<String, Double> categoryBudgets;
    private LocalDateTime createdAt;
}
