package com.exploreceylon.backend.dto.budget;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class BudgetResponse {
    private Long id;
    private Long tripId;
    private String tripTitle;
    private Double totalBudget;
    private String currency;
    private List<BudgetItemResponse> items;
    private LocalDateTime createdAt;
}
