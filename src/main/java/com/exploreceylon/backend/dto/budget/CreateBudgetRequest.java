package com.exploreceylon.backend.dto.budget;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateBudgetRequest {

    @NotNull(message = "Trip ID is required")
    private Long tripId;

    @NotNull(message = "Total budget is required")
    private Double totalBudget;

    private String currency;
}